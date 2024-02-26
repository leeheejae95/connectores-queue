package com.fastcampus.flow.service;

import com.fastcampus.flow.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserQueueService {

    private final ReactiveRedisTemplate<String,String> reactiveRedisTemplate;

    private final String USER_QUEUE_WAIT_KEY = "users:queue:%s:wait"; // 사용자 대기
    private final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "users:queue:*:wait"; // 대기열을 찾아 각각을 허용해주는 키
    private final String USER_QUEUE_PROCEED_KEY = "users:queue:%s:proceed"; // 사용자 허용

    @Value("${scheduler.enabled}")
    private Boolean scheduling = false;

    // 대기열 등록 API 개발
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        // redis sortedset에 저장 -> opsForZSet().add()
        // key : userId -> opsForZSet().add
        // value : unix timestamp -> 시간을 줘서 먼저 등록한 사람이 높은 순위를 갖게끔
        var unixTimestamp = Instant.now().getEpochSecond();
        return reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_WAIT_KEY.formatted(queue),userId.toString(), unixTimestamp)
                .filter(i -> i) // true인 경우
                .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build())) // true가 아닐 경우
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())) // 대기순
                .map(i -> i >=0 ? i+1: i);
    }
    
    // 진입을 허용하는 로직
    public Mono<Long> allowUser(final String queue, final Long count) {
        // 진입을 허용하는 단계
        // 1. wait queue 사용자를 제거하고
        // 2. proceed queue 시용자를 추가 (요청수는 5개지만 실제로는 3개만 저리됨)
        // popMin -> value값이 작은 멤버를 뺴줌
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue),count) // user-queue에서 해당 갯수만큼 꺼냄 
                .flatMap(member -> reactiveRedisTemplate.opsForZSet()
                        .add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond())) // USER_QUEUE_PROCEED_KEY에 다시 add함
                .count();
    }

    // 진입이 가능한 상태인지 조회로직
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map( rank -> rank >= 0);
    }

    public Mono<Boolean> isAllowedByToken(final String queue, final Long userId, final String token) {
        return this.generateToken(queue,userId)
                .filter(gen -> gen.equalsIgnoreCase(token))
                .map(i -> true)
                .defaultIfEmpty(false);
    }

    // 대기번호 체크
    public Mono<Long> getRank(String queue, Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map( rank -> rank >=0? rank + 1: rank);
    }

    public Mono<String> generateToken(final String queue, final Long userId) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");

            var input = "user-queue-%s-%d".formatted(queue,userId);
            byte[] encodeHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for(byte aByte : encodeHash) {
                hexString.append(String.format("%02x", aByte));
            }

            return Mono.just(hexString.toString());

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // @Scheduled를 사용하면 아래 메서드를 주기적으로 실행 시켜줌
    @Scheduled(initialDelay = 5000, fixedDelay = 10000) // 서버가 시작하고 5초정도 쉬었다가 그 이후로 3초 주기로 실행해줘
    public void scheduleAllowUser() {
        if (!scheduling) {
            log.info("passed scheduling...");
            return;
        }
        log.info("called scheduling...");

        var maxAllowUserCount = 100L;

        // 사용자를 허용하는 코드 작성
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                        .count(100)
                        .build())
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed)))
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
                .subscribe();
    }

}
