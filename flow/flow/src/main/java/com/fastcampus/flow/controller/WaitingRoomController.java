package com.fastcampus.flow.controller;

import com.fastcampus.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 대기 웹페이지를 보여줄 컨트롤러
 */
@Controller
@RequiredArgsConstructor
public class WaitingRoomController {

    private final UserQueueService userQueueService;
    
    @GetMapping("/waiting-room")
    public Mono<Rendering> waitingRoomPage(@RequestParam(name = "queue", defaultValue = "dafault") String queue,
                                           @RequestParam(name = "user_id") Long userId,
                                           @RequestParam(name = "redirect_url") String redirectUrl,
                                           ServerWebExchange exchange
    ) {
        var key = "user-queue-%s-token".formatted(queue);
        var cookieValue = exchange.getRequest().getCookies().getFirst(key);
        var token = (cookieValue == null) ? "" : cookieValue.getValue();

        // 1. 입장이 허용되어 page redirect(이동)이 가능한 상태인가?
        return userQueueService.isAllowedByToken(queue, userId, token)
                .filter(allowed -> allowed) // 입장이 허용 됐는지
                .flatMap(allowed -> Mono.just(Rendering.redirectTo(redirectUrl).build())) // 2. 어디로 이동해야 하는가?
                .switchIfEmpty(userQueueService.registerWaitQueue(queue, userId) // 대기페이지
                        .onErrorResume( ex -> userQueueService.getRank(queue,userId))
                        // 웹페이지에 필요한 데이터 전달
                        .map( rank -> Rendering.view("waiting-room.html")
                                .modelAttribute("number", rank)
                                .modelAttribute("userId", userId)
                                .modelAttribute("queue", queue)
                                .build()
                        ));
    }
}
