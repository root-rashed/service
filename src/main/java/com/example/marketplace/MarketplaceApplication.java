package com.example.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication(exclude = {
        org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration.class
})
@EnableCaching
public class MarketplaceApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(MarketplaceApplication.class, args);
    }
}
