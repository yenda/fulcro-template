(ns app.client
  (:require
   [app.application :refer [SPA]]
   [app.ui.root :as root]
   [com.fulcrologic.fulcro-css.css-injection :as cssi]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [taoensso.timbre :as log]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (cssi/upsert-css "componentcss" {:component root/Root})
  (app/mount! SPA root/Root "app"))

(defn ^:export init []
  (log/info "Application starting.")
  (cssi/upsert-css "componentcss" {:component root/Root})
  ;(inspect/app-started! SPA)
  (app/set-root! SPA root/Root {:initialize-state? true})
  (dr/initialize! SPA)
  (log/info "Starting session machine.")
  (app/mount! SPA root/Root "app" {:initialize-state? false}))
