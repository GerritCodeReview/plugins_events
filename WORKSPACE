workspace(name = "events")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "70a1881866e8aecc21561f0815146b83b430d3c7",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()
