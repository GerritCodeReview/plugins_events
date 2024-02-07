workspace(name = "events")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "e977766e5670dca125c502c37339e9f3da9b329b",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()
