package gloss.domain.account.security

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.GenericConverter
import org.springframework.core.convert.support.GenericConversionService
import org.springframework.core.serializer.support.DeserializingConverter
import org.springframework.core.serializer.support.SerializingConverter

@Configuration
class SessionConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Spring Session JDBC stores attributes with Java serialization, which names each class.
     * After a class moves, an old session no longer reads; drop the attribute so the reader is
     * signed out, rather than answering every request with that cookie with a 500.
     */
    @Bean("springSessionConversionService")
    fun springSessionConversionService(): ConversionService {
        val deserializer = DeserializingConverter(javaClass.classLoader)
        return GenericConversionService().apply {
            addConverter(Any::class.java, ByteArray::class.java, SerializingConverter())
            addConverter(
                object : GenericConverter {
                    override fun getConvertibleTypes() =
                        setOf(GenericConverter.ConvertiblePair(ByteArray::class.java, Any::class.java))

                    override fun convert(
                        source: Any?,
                        sourceType: TypeDescriptor,
                        targetType: TypeDescriptor,
                    ): Any? =
                        runCatching { deserializer.convert(source as ByteArray) }
                            .onFailure {
                                log.warn(
                                    "Dropped a session attribute that no longer reads: {}",
                                    it.javaClass.name,
                                )
                            }.getOrNull()
                },
            )
        }
    }
}
