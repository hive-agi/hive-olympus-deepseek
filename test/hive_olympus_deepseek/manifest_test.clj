(ns hive-olympus-deepseek.manifest-test
  "The shipped manifest through the real mounter against a stub hive.olympus
   (a presenter seat) and a stub hive.deepseek (a :vessel/dispatch! host),
   then against the real hive.olympus core manifest."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [hive-addon.mount :as mount]
            [hive-addon.mount.port :as mount-port]
            [hive-addon.protocol :as addon]))

(defrecord StubAddon [id hook-map]
  addon/IAddon
  (addon-id [_] id)
  (addon-type [_] :native)
  (capabilities [_] #{})
  (initialize! [_ _] {:success? true})
  (shutdown! [_] nil)
  (tools [_] [])
  (schema-extensions [_] [])
  (health [_] {:status :ok})
  (excluded-tools [_] #{})
  (hooks [_] hook-map))

(def seat (atom {}))
(def dispatched (atom []))

(def panel
  {:op :ui/show-panel :panel/id "olympus/tab-1"
   :doc {:doc/title "Olympus  tab 1/1  (0 agents: 0 working, 0 blocked, 0 error, 0 idle)"
         :doc/blocks [{:block/type :para :text "No active agents" :tone :muted}]}})

(defn olympus-stub-ctor [_]
  (->StubAddon "hive.olympus"
               {:olympus/register-presenter! (fn [id target] (swap! seat assoc id target) (target [panel]) id)
                :olympus/unregister-presenter! (fn [id] (swap! seat dissoc id) id)}))

(defn deepseek-stub-ctor [_]
  (->StubAddon "hive.deepseek"
               {:vessel/dispatch! (fn [ops] (swap! dispatched conj ops) {:ok {:plan/ops ops}})}))

(defn no-agents [] [])

(defn- manifest [file]
  (some-> (io/resource (str "META-INF/hive-addons/" file)) slurp edn/read-string))

(def deepseek-stub-spec
  {:addon/id "hive.deepseek" :addon/type :native
   :addon/init-ns "hive-olympus-deepseek.manifest-test" :addon/init-fn "deepseek-stub-ctor"
   :addon/capabilities #{:vessel}})

(defn- mount-all [specs]
  (let [host (mount/atom-mount-host)
        report (mount/mount! (mount/solve specs) host)]
    [host report]))

(deftest the-manifest-is-data-only
  (let [spec (manifest "hive-olympus-deepseek.edn")]
    (is (= "hive.olympus.deepseek" (:addon/id spec)))
    (is (= "hive-olympus.harness" (:addon/init-ns spec)))
    (is (= {:olympus/host "hive.deepseek"} (:addon/config spec)))
    (is (= #{"hive.olympus" "hive.deepseek"} (:addon/dependencies spec)))
    (is (= :foss (:addon/trust-class spec)))
    (is (some #(= "hive.olympus.deepseek" (:addon/id %)) (:specs (mount/discover-specs))))))

(deftest mounts-against-stubs-and-delivers
  (reset! seat {})
  (reset! dispatched [])
  (let [[host report] (mount-all [(manifest "hive-olympus-deepseek.edn")
                                  {:addon/id "hive.olympus" :addon/type :native
                                   :addon/init-ns "hive-olympus-deepseek.manifest-test"
                                   :addon/init-fn "olympus-stub-ctor" :addon/capabilities #{}}
                                  deepseek-stub-spec])
        brick (mount-port/registered host "hive.olympus.deepseek")]
    (try
      (is (:ok? report) (pr-str (:mounted report)))
      (is (= "hive.olympus.deepseek" (last (:order report))))
      (is (contains? @seat "hive.deepseek") "registered under the host id")
      (is (= [[panel]] @dispatched) "the seat's ops reach hive.deepseek's dispatch verbatim")
      (is (= :host-dispatch (get-in (addon/health brick) [:details :route])))
      (is (empty? (:errors (mount/teardown! host (:order report)))))
      (is (empty? @seat) "teardown unregisters the presenter")
      (finally (when brick (addon/shutdown! brick))))))

(deftest mounts-against-the-real-core
  (reset! dispatched [])
  (let [core-spec (update (manifest "hive-olympus.edn") :addon/config assoc
                          :olympus/refresh-ms 0
                          :olympus/roster-fn 'hive-olympus-deepseek.manifest-test/no-agents)
        [host report] (mount-all [(manifest "hive-olympus-deepseek.edn") core-spec deepseek-stub-spec])
        core (mount-port/registered host "hive.olympus")]
    (try
      (is (:ok? report) (pr-str (:mounted report)))
      (testing "the real core renders the empty grid and the brick delivers it"
        (is (= [[panel]] @dispatched))
        (is (= {:status :live :deliveries 1}
               (get-in (addon/health core) [:details :presenters "hive.deepseek"]))))
      (mount/teardown! host (:order report))
      (finally (doseq [id ["hive.olympus.deepseek" "hive.olympus"]]
                 (when-let [a (mount-port/registered host id)]
                   (try (addon/shutdown! a) (catch Throwable _ nil))))))))
