(ns app.ui.root
  (:require
   ["/app/ui/dividerBlot$default" :as divider-blot]
   ["highlight.js" :as highlight]
   ["quill" :as Quill]
   ["quill-delta" :as Delta]
   ["quill-image-compress$default" :as ImageCompress]
   [app.model.session :as session]
   [clojure.string :as str]
   [com.fulcrologic.fulcro-css.css :as css]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.dom :as dom :refer [b button div h3 li p ul]]
   [com.fulcrologic.fulcro.dom.events :as evt]
   [com.fulcrologic.fulcro.dom.html-entities :as ent]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.react.hooks :as hooks]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
   [taoensso.timbre :as log]))

(def empty-delta "{\"ops\":[{\"insert\":\"\\n\"}]}")
(def delta-min-size (count empty-delta))

(defonce quill-register
  (doto Quill
    (.register "modules/imageCompress" ImageCompress)
    (.register divider-blot)))

(defn insert-into-editor [editor-ref]
  (fn [image-base64-url image-blob]
    (let [editor (some-> editor-ref .-current)
          range (.getSelection editor)]
      (when range
        ^js (.insertEmbed editor (.-index range) "image" image-base64-url "user")
        (set! (.-index range) (inc (.-index range)))
        (.getSelection editor range "api")))))

(defn quill-config [insert-into-editor-with-ref]
  {:modules {:syntax {:highlight (fn [text language]
                                   (-> highlight
                                       (.highlightAuto text)
                                       (.-value))
                                            ;; TODO: ideally we use that code when language
                                            ;; can be specified for a codeblock in quill
                                   #_(if language
                                       (-> highlight
                                           (.highlight text #js {:language language})
                                           (.-value))
                                       text))}
             :toolbar [[{:header []}]
                       ["bold" "italic" "underline"]
                       [{:list "bullet"}
                        {:list "ordered"}]
                       [{:script "sub"} {:script "super"}]
                       [{:indent "-1"} {:indent "+1"}]
                       ["blockquote" "code-block"]
                       ["link" "image" "formula" "video"]
                       ["clean"]]
             :imageCompress {:quality 0.7
                             :maxWidth 1920
                             :maxHeight 1920
                             :mimeType "image/png"
                             :imageType "image/png"
                             :debug false
                             :insertIntoEditor insert-into-editor-with-ref}}
   :theme "snow"})

(defsc Editor [this {:keys [value onChange toolbar] :as props}]
  {:use-hooks? true}
  (let [^:js editorElement (hooks/use-ref)
        ^:js editorInstance (hooks/use-ref)
        insert-into-editor-with-ref (hooks/use-callback (insert-into-editor editorInstance)
                                                        [editorInstance])
        [currentValue setCurrentValue] (hooks/use-state (or value
                                                            empty-delta))
        _ (hooks/use-effect
           (fn []
             (when-let [editor-element (.-current editorElement)]
               (let [quill-instance (cond-> (quill-config insert-into-editor-with-ref)
                                      toolbar (assoc-in [:modules :toolbar] toolbar)
                                      :always (->> clj->js (Quill. editor-element)))]
                 (set! (.-current editorInstance) quill-instance)
                 (.setContents (.-current editorInstance)
                               (Delta. (.parse js/JSON currentValue)))
                 (.on (.-current editorInstance) "text-change"
                      (fn []
                        (let [delta-str (.stringify js/JSON
                                                    ^js (.-delta
                                                         (.-editor
                                                          (.-current editorInstance))))]
                          (setCurrentValue delta-str)
                          (when onChange
                            (onChange delta-str)))))))
             (fn []))
           [editorElement])
        _ (hooks/use-effect (fn []
                              (when (not= (js->clj (.parse js/JSON currentValue))
                                          (js->clj (.parse js/JSON value)))
                                (setCurrentValue value)
                                (.setContents (.-current editorInstance)
                                              (Delta. (.parse js/JSON value))))
                              (fn []))
                            [value])]
    (dom/div
     {:className "editor notranslate"}
     (dom/div {:ref editorElement}))))

(def editor (comp/factory Editor))

(defsc Root [this {:root/keys [top-chrome]}]
  {}
  (dom/div
   (editor {:value "{\"ops\":[{\"insert\":{\"divider\":true}},{\"insert\":\"\\n\"}]}"
            :onChange (fn [_])})))
