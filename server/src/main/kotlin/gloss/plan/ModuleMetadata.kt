package gloss.plan

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "plan",
    allowedDependencies = [
        "common",
        "book :: application",
        "book :: event",
        "budget :: application",
        "generation :: application",
        "generation :: job",
        "llm :: application",
        "llm :: chat",
    ],
)
class ModuleMetadata
