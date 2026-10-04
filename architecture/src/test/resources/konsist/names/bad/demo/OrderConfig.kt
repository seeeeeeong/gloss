package demo

@Configuration
class OrderConfig {
    @Bean
    fun orderFilter(ordersStore: OrderRepository): OrderFilter = OrderFilter(ordersStore)
}
