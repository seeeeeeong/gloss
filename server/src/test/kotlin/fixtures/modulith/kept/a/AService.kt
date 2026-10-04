package fixtures.modulith.kept.a

import fixtures.modulith.kept.a.internal.Detail

class AService {
    private val detail = Detail()

    fun name() = detail.toString()
}
