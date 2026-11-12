package com.example.app.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring IoC configuration class for instantiating pure Java Interactors / UseCases as Spring Beans.
 */
@Configuration
public class InteractorConfig {
    // Declare your UseCase / Interactor beans here:
    // e.g.:
    // @Bean
    // public CreateOrderUseCase createOrderUseCase(GenericPort<UUID, Order> orderPort) {
    //     return new CreateOrderInteractor(orderPort);
    // }
}
