workspace(name = "events")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "e68cc7a45d9ee2b100024b9b12533b50a4598585",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()
