package gloss.book.domain.event

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

// What other modules may listen to: "book :: event".
@PackageInfo
@NamedInterface("event")
class PackageMetadata
