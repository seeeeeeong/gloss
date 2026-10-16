package gloss.domain.llm.service

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

// The one way another module calls a model: "llm :: service".
@PackageInfo
@NamedInterface("service")
class PackageMetadata
