(set-env!
 :source-paths #{"src/clj" "src/cljs"}
 :resource-paths #{"res"}
 :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.0" :scope "test"]
                 [adzerk/boot-reload "0.4.8" :scope "test"]
                 [pandeiro/boot-http "0.7.3" :scope "test"]
                 [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"]
                 [boot-environ "1.0.3"]
                 [cljs-react-material-ui "0.2.37"]
                 [clj-time "0.13.0"]
                 ;; using the alpha because that's the version of the API docs
                 ;; in their website.
                 [com.andrewmcveigh/cljs-time "0.5.0-alpha2"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.36"]
                 [compojure "1.4.0"]
                 [org.clojure/core.async "0.2.374"]
                 [crypto-random "1.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [datascript "0.15.2"]
                 [com.datomic/datomic-free "0.9.5544"]
                 [environ "1.0.3"]
                 [hiccup "1.0.5"]
                 ;; used for the sente adapter in development, and for the http
                 ;; client
                 [http-kit "2.1.19"]  ;; same as used by boot-http
                 [org.clojars.euccastro/markdown-clj "0.9.89+literal"]
                 ;; used for the sente adapter in deployment
                 [nginx-clojure "0.4.4"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [posh "0.5.3.3"]
                 [reagent "0.6.1-synth3" :exclusions [org.clojure/tools.reader cljsjs/react cljsjs/react-dom]]
                 [ring/ring-defaults "0.1.5"]
                 [com.taoensso/sente "1.10.0"]
                 [com.taoensso/timbre "4.7.2"]
                 [org.clojars.euccastro/spaced-repetition "0.1.3-SNAPSHOT"]
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
     (cljs-repl)
     (cljs :source-map true :optimizations :none)
     (target :dir #{"target"})))

(deftask build []
  (comp
   (cljs :optimizations :advanced)
   (aot :namespace '#{relembra.core})
   (pom :project 'relembra
        :version "0.1.0-SNAPSHOT")
   (uber)
   (jar :main 'relembra.core)
   (target :dir #{"target"})))
