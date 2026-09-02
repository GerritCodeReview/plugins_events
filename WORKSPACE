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
gerrit_api(
    version = "3.13.8",
    plugin_api_sha1 = "4ddec4be65f9f3245073b270348710ed32b32f02",
    acceptance_framework_sha1 = "aabfb5b26bfe1a0c803fccbf199faba942f51b3f",
)
