package gloss.generation.domain.job

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

// What a caller of GenerationService.enqueue builds: "generation :: job".
@PackageInfo
@NamedInterface("job")
class PackageMetadata
