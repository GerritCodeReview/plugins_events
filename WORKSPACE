workspace(name = "events")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "b1d205acd376e48cf79beb8499fb0f9a824a217b",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

register_toolchains("//tools:all")
