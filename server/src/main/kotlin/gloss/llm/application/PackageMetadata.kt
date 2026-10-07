package gloss.llm.application

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

// The one way another module calls a model: "llm :: application".
@PackageInfo
@NamedInterface("application")
class PackageMetadata
