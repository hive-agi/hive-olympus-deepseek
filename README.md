# hive-olympus-deepseek

Olympus in the DeepSeek Harness (dsh). This brick has no code: it is one
manifest that mounts `hive-olympus.harness/addon-ctor` with
`{:olympus/host "hive.deepseek"}`. The core (`hive.olympus`) renders the agent
grid as one `:ui/show-panel` per tab; the brick hands those ops to
`hive.deepseek`'s `:vessel/dispatch!`, and dsh shows them in its Hive sidebar.

```
resources/META-INF/hive-addons/hive-olympus-deepseek.edn
```

Requires `hive.olympus` and `hive.deepseek` mounted in the same hive.

## Test

hive-olympus is not yet published, so point at a sibling checkout:

```
clojure -Sdeps "$(cat local.deps.edn)" -M:test
```

with an untracked `local.deps.edn`:

```clojure
{:deps {io.github.hive-agi/hive-olympus {:local/root "../hive-olympus"}}}
```

MIT licensed.
