package demo

@Configuration
class OrderConfig {
    @Bean
    fun orderFilter(
        orderRepository: OrderRepository,
        httpSecurity: HttpSecurity,
    ): OrderFilter = OrderFilter(orderRepository)

    fun helper(repo: OrderRepository) = repo
}
