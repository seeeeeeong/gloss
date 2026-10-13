package gloss.generation

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "generation",
    allowedDependencies = [
        "common",
        "account :: application",
        "account :: event",
        "budget :: application",
        "llm :: application",
        "llm :: chat",
    ],
)
class ModuleMetadata
