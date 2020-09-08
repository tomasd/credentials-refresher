# Credentials refresher

## Install

Clone from the repo 

```
git clone https://github.com/tomasd/credentials-refresher.git
```

Install to local repository:

```
make install
```

Add to ~/.lein/profiles.clj new entry:

```clojure
:credentials-refresher {:dependencies [[credentials-refresher/credentials-refresher "0.1.0"]]
                        :injections [(require 'credentials-refresher.core)
                                     (credentials-refresher.core/start!)]}
```


# Configuration

Use the following environment variables:
- `CR_EMAIL`
- `CR_PASSWORD`
- `CR_ROLE`

Now if you start the lein repl with `+credentials-refresher` it will automatically 
log you in and refresh the token in the background.