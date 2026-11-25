# Release builds are not minified, shrunk or obfuscated yet (isMinifyEnabled =
# false in app/build.gradle.kts), so these rules are currently inert.
#
# When minification is turned on, keep at least:
#  - kotlinx.serialization @Serializable classes and their generated
#    serializers (data/Conversations.kt, data/Models.kt), which are decoded by
#    reflection-free generated code R8 can otherwise strip or rename;
#  - anything OkHttp's published consumer rules don't already cover.
# There is no Cloudflare Agents SDK in this app: the protocol is implemented
# directly in network/AgentWebSocket.kt over OkHttp and org.json.
