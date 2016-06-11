(set-env!
 :source-paths #{"src/clj" "src/cljs" "src/cljc"}
 :resource-paths #{"res"}
 :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.0" :scope "test"]
                 [adzerk/boot-reload "0.4.8" :scope "test"]
                 [pandeiro/boot-http "0.7.3" :scope "test"]
                 [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"]
                 [boot-environ "1.0.3"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.36"]
                 [compojure "1.4.0"]
                 [org.clojure/core.async "0.2.374"]
                 [crypto-random "1.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [datascript "0.15.0"]
                 [com.datomic/datomic-free "0.9.5372"]
                 [environ "1.0.3"]
                 [hiccup "1.0.5"]
                 ;; used for the sente adapter in development, and for the http
                 ;; client
                 [http-kit "2.1.19"]  ;; same as used by boot-http
                 ;; used for the sente adapter in deployment
                 [nginx-clojure "0.4.4"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [posh "0.3.5"]
                 [reagent "0.6.0-alpha"]
                 [ring/ring-defaults "0.1.5"]
                 [com.taoensso/sente "1.8.1"]
                 [org.clojure/tools.nrepl "0.2.12" :scope "test"]
                 [com.cemerick/url "0.1.1"]
                 [weasel "0.7.0" :scope "test"]])

(require
  '[adzerk.boot-cljs      :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload    :refer [reload]]
  '[environ.boot :refer [environ]]
  '[crisptrutski.boot-cljs-test  :refer [test-cljs]]
  '[pandeiro.boot-http    :refer [serve]])

(deftask data-readers []
  (fn [next-task]
    (fn [fileset]
      (#'clojure.core/load-data-readers)
      (with-bindings {#'*data-readers* (.getRawRoot #'*data-readers*)}
        (next-task fileset)))))

(deftask auto-test []
  (merge-env! :resource-paths #{"test"})
  (comp (watch)
     (speak)
     (test-cljs)))

(deftask dev []
  (comp (environ :env {:in-development "indeed"})
     (serve :handler 'relembra.core/app
            :resource-root "target"
            :httpkit true
            :reload true)
     (watch)
     (speak)
     (reload :on-jsload 'relembra.core/main
             ;; XXX: make this configurable
             :open-file "emacsclient -n +%s:%s %s")
     (data-readers)
     (cljs-repl)
     (cljs :source-map true :optimizations :none)
     (target :dir #{"target"})))

(deftask build []
  (comp
   (data-readers)
   (cljs :optimizations :advanced)
   (aot :namespace '#{relembra.core})
   (pom :project 'relembra
        :version "0.1.0-SNAPSHOT")
   (uber)
   (jar :main 'relembra.core)
   (target :dir #{"target"})))
