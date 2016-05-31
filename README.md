# relembra

Repetition spacing webapp.

## Play with it

- [Install boot](https://github.com/boot-clj/boot#install),

- Set `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` environment variables to the values found in [this test github app](https://github.com/settings/applications/360161) (or create one with an Authorization callback URL of http://127.0.0.1:3000/github-auth-cb).

- clone this repo,

- `cd` to its root directory,

- run `boot dev`,

- browse to http://127.0.0.1:3000/ (actually use `127.0.0.1` and not `localhost` or other alias.  You'll trigger a CSRF error the first time you log in otherwise, because the abovementioned github app is configured with a callback address using `127.0.0.1`.)

- connect to local [nRepl](https://github.com/clojure/tools.nrepl) via [cider](https://github.com/clojure-emacs/cider) (if using emacs) or by opening `boot repl -c` from a different shell window.

- Server-side logic goes in `src/clj/relembra`.  To see the effect of server-side changes, save the relevant `.clj` files and reload the page,

- Client-side logic is in `src/cljs/relembra`.  Static assets are in `/res/public`.  Changes in both should be shown on save; no page reload necessary.

An [http-kit](http://www.http-kit.org/) server is used for development.  This is because it's the only option supported natively by both [sente](https://github.com/ptaoussanis/sente) and [boot-http](https://github.com/pandeiro/boot-http).

If you're interested in dev tooling setup, [this tutorial](https://github.com/magomimmo/modern-cljs/blob/master/doc/second-edition/tutorial-01.md) is a more leisurely introduction to boot and related tools.

## Deploying

This code is deployed to [`relembra.estevo.eu`](https://relembra.estevo.eu).

### tl;dr

```bash
ssh relembra.estevo.eu
cd relembra
git pull
script/deploy.sh
```

### Details

The production deployment uses [nginx-clojure](https://github.com/nginx-clojure/nginx-clojure) as the web server.  This was chosen for having native support for TLS and for sente[1].

Our deployment of nginx-clojure was set up manually, roughly following [these steps](https://github.com/nginx-clojure/nginx-clojure/tree/master/example-projects/clojure-web-example).  The only custom parts are the files in [`etc`](https://github.com/getlantern/ops-panel/tree/master/etc).  The (so far unoptimized) configuration for the web server is in [`etc/nginx.conf`](https://github.com/getlantern/ops-panel/blob/master/etc/nginx.conf).  The systemd configuration for this server is in [`etc/nginx.system`](https://github.com/getlantern/ops-panel/blob/master/etc/nginx.system).  Environment variables are edited directly in `/etc/environment` at the production machine.

To create an [uberjar](http://stackoverflow.com/questions/11947037/what-is-an-uber-jar) for deployment run `boot build`.  This will create a `target/project.jar` which should replace `/opt/nginx-clojure-0.4.4/libs/relembra.jar`.  Since uploading this file may be onerous, a clone of this repo is checked out in `$HOME/relembra` in the production machine, so you can build it there and then copy it (again, `script/deploy.sh` does that).
