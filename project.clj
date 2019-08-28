(defproject day8.re-frame/undo "0.3.3-SNAPSHOT"
  :description  "A library which provides undo/redo facility for re-frame"
  :license      {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs "2.8.52" :scope "provided"]
                 [re-frame "0.10.9" :scope "provided"]]

  :plugins [[lein-shadow "0.1.5"]]

  :profiles {:debug {:debug true}
             :dev   {:dependencies [[binaryage/devtools "0.9.10"]]
                     :plugins      [[lein-ancient       "0.6.15"]
                                    [lein-shell         "0.5.0"]]}}

  :clean-targets  [:target-path
                   "resources/public/js/test"]

  :resource-paths ["run/resources"]
  :jvm-opts       ["-Xmx1g" "-XX:+UseConcMarkSweepGC"]
  :source-paths   ["src"]
  :test-paths     ["test"]

  :shell          {:commands {"open" {:windows ["cmd" "/c" "start"]
                                      :macosx  "open"
                                      :linux   "xdg-open"}}}

  ;; > lein deploy
  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]

  ;; > lein release
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :shadow-cljs {:nrepl  {:port 8777}

                :builds {:browser-test
                         {:target    :browser-test
                          :ns-regexp "-test$"
                          :test-dir  "resources/public/js/test"
                          :compiler-options {:pretty-print true}
                          :devtools  {:http-root "resources/public/js/test"
                                      :http-port 8290}}

                         :karma-test
                         {:target    :karma
                          :ns-regexp "-test$"
                          :compiler-options {:pretty-print true}
                          :output-to "target/karma-test.js"}}}

  :aliases {"test-auto"   ["do"
                           ["clean"]
                           ["shadow" "watch" "browser-test"]]
            "karma-once"  ["do"
                           ["clean"]
                           ["shadow" "compile" "karma-test"]
                           ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]})
