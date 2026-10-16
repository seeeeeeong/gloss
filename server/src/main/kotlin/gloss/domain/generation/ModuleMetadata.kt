package gloss.domain.generation

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "generation",
    allowedDependencies = [
        "common",
        "account :: service",
        "account :: event",
        "budget :: service",
        "llm :: service",
        "llm :: chat",
    ],
)
class ModuleMetadata
