workspace(name = "events")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "ea5aa9f256fe94577accb074f1f51e12af106708",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()
