package gloss.domain.chat

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "chat",
    allowedDependencies = [
        "common",
        "book :: service",
        "budget :: service",
        "llm :: service",
        "llm :: chat",
        "plan :: service",
        "post :: service",
    ],
)
class ModuleMetadata
