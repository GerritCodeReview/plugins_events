workspace(name = "events")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "7ed39252845169ef23a7561b6b429e31a3abfb67",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api(version = "3.12.0",
               plugin_api_sha1 = "05305041aa39110cea433bd898781c5d6b6c4a73",
               acceptance_framework_sha1 = "7eafc09d32b1ab5cc5afde790b6b4f4691f83ce3")
