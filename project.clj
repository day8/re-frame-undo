(defproject     day8.re-frame/undo "lein-git-inject/version"
  :description  "A library which provides undo/redo facility for re-frame"
  :license      {:name "MIT"}

  :dependencies [[org.clojure/clojure       "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.597" :scope "provided"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]
                 [thheller/shadow-cljs      "2.8.83" :scope "provided"]
                 [re-frame                  "0.10.9" :scope "provided"]]

  :plugins      [[day8/lein-git-inject "0.0.6"]
                 [lein-shadow          "0.1.7"]]

  :middleware   [leiningen.git-inject/middleware]

  :profiles {:debug {:debug true}
             :dev   {:dependencies [[binaryage/devtools "0.9.11"]]
                     :plugins      [[lein-ancient       "0.6.15"]
                                    [lein-shell         "0.5.0"]]}}

  :clean-targets  [:target-path
                   "resources/public/js/test"]

  :resource-paths ["run/resources"]
  :jvm-opts       ["-Xmx1g"]
  :source-paths   ["src"]
  :test-paths     ["test"]

  :shell          {:commands {"open" {:windows ["cmd" "/c" "start"]
                                      :macosx  "open"
                                      :linux   "xdg-open"}}}
  
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_PASSWORD}]]
  
  :release-tasks [["deploy" "clojars"]]

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
