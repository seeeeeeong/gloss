package gloss.common

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@ExtendWith(OutputCaptureExtension::class)
class GlobalExceptionHandlerTest {
    enum class SampleError(
        override val status: HttpStatus,
        override val code: String,
        override val message: String,
    ) : ErrorCode {
        TAKEN(HttpStatus.CONFLICT, "sample.taken", "이미 있습니다"),
    }

    data class SampleRequest(
        @field:NotBlank val name: String,
    )

    @RestController
    class SampleController {
        @GetMapping("/taken")
        fun taken(): Unit = throw GlossException(SampleError.TAKEN)

        @PostMapping("/named")
        fun named(
            @Valid @RequestBody request: SampleRequest,
        ) = request.name

        @GetMapping("/broken")
        fun broken(): Unit = error("database password is hunter2")
    }

    private val mvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(SampleController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `a thrown error code answers with its status, code and message`() {
        mvc
            .perform(get("/taken"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("sample.taken"))
            .andExpect(jsonPath("$.message").value("이미 있습니다"))
    }

    @Test
    fun `a request that fails validation is an invalid input`() {
        mvc
            .perform(post("/named").contentType(MediaType.APPLICATION_JSON).content("""{"name":""}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("common.invalid_input"))
    }

    @Test
    fun `a body that cannot be read is an invalid input`() {
        mvc
            .perform(post("/named").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("common.invalid_input"))
    }

    @Test
    fun `a path nothing serves stays a 404`() {
        mvc
            .perform(get("/missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("common.not_found"))
    }

    @Test
    fun `a method a path does not take stays a 405`() {
        mvc
            .perform(post("/taken"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("common.method_not_allowed"))
    }

    @Test
    fun `another 4xx of Spring's keeps its status`() {
        mvc
            .perform(post("/named").contentType(MediaType.TEXT_PLAIN).content("name"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("common.unsupported_media_type"))
    }

    @Test
    fun `an unexpected failure is logged by type and place, never by its message`(output: CapturedOutput) {
        val message =
            mvc
                .perform(get("/broken"))
                .andReturn()
                .response.contentAsString
                .let { Regex("오류 번호 ([0-9a-f]{8})").find(it)!!.groupValues[1] }

        assertThat(output.out).contains("IllegalStateException", message, "GlobalExceptionHandlerTest")
        assertThat(output.out).doesNotContain("hunter2")
    }

    @Test
    fun `an unexpected failure is a 500 that tells nothing of its cause`() {
        val body =
            mvc
                .perform(get("/broken"))
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.code").value("common.internal"))
                .andReturn()
                .response.contentAsString

        assertThat(body).doesNotContain("hunter2")
    }
}
