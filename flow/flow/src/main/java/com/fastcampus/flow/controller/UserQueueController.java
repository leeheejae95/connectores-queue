package com.fastcampus.flow.controller;

import com.fastcampus.flow.dto.AllowUserResponse;
import com.fastcampus.flow.dto.AllowedUserResponse;
import com.fastcampus.flow.dto.RankNumberResponse;
import com.fastcampus.flow.dto.RegisterUserResponse;
import com.fastcampus.flow.service.UserQueueService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class UserQueueController {

    private final UserQueueService userQueueService;

    // 등록할 수 있는 API path지정
    @PostMapping("")
    public Mono<RegisterUserResponse> registerUSer(@RequestParam(name = "queue", defaultValue = "default") String queue
            , @RequestParam(name = "user_id") Long userId) {
        return userQueueService.registerWaitQueue(queue, userId)
                .map(RegisterUserResponse::new);
    }

    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam(name = "count") Long count) {
        return userQueueService.allowUser(queue, count)
                .map(allowed -> new AllowUserResponse(count, allowed)); // 몇개가 request됐고 몇개가 허용 됐는지

    }

    @GetMapping("/allowed")
    public Mono<AllowedUserResponse> isAllowed(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                               @RequestParam(name = "user_id") Long userId,
                                               @RequestParam(name = "token") String token
    ) {
        return userQueueService.isAllowedByToken(queue, userId, token)
                .map( allowed -> new AllowedUserResponse(allowed) );

    }

    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue") String name,
                                                @RequestParam("user_id") Long userId) {
        return userQueueService.getRank(name, userId)
                .map( rank -> new RankNumberResponse(rank));

    }

    @GetMapping("/touch")
    public Mono<?> touch(@RequestParam(name = "queue", defaultValue = "default") String queue,
                         @RequestParam(name = "user_id") Long userId,
                         ServerWebExchange exchange
    ) {
        return Mono.defer(() -> userQueueService.generateToken(queue,userId))
                .map(token -> {
                    exchange.getResponse().addCookie(
                            ResponseCookie
                                    .from("user-queue-%s-token".formatted(queue), token)
                                    .maxAge(Duration.ofSeconds(3000))
                                    .path("/")
                                    .build()
                    );

                    return token;
                });
    }

}
