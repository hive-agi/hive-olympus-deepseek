(ns hive-olympus-deepseek.test-runner
  (:require [clojure.test :as test]
            [hive-olympus-deepseek.manifest-test]))

(defn -main
  [& _]
  (let [{:keys [fail error]} (test/run-tests 'hive-olympus-deepseek.manifest-test)]
    (shutdown-agents)
    (System/exit (if (pos? (+ fail error)) 1 0))))
