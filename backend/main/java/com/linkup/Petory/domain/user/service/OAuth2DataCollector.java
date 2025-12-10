package com.linkup.Petory.domain.user.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2에서 받아온 모든 데이터를 임시로 저장하는 전역 컬렉터
 * DB에 저장하지 않는 값들도 확인할 수 있도록 함
 */
public class OAuth2DataCollector {
    
    // Provider별로 받아온 모든 원본 데이터 저장
    private static final Map<String, Map<String, Object>> providerDataMap = new ConcurrentHashMap<>();
    
    /**
     * Provider별 원본 데이터 저장
     */
    public static void saveProviderData(String provider, Map<String, Object> data) {
        providerDataMap.put(provider.toLowerCase(), data);
        System.out.println("========================================");
        System.out.println("📦 OAuth2DataCollector에 저장됨:");
        System.out.println("Provider: " + provider);
        System.out.println("데이터 크기: " + data.size() + "개 필드");
        System.out.println("========================================");
    }
    
    /**
     * 저장된 데이터 조회
     */
    public static Map<String, Object> getProviderData(String provider) {
        return providerDataMap.get(provider.toLowerCase());
    }
    
    /**
     * 모든 저장된 데이터 조회
     */
    public static Map<String, Map<String, Object>> getAllData() {
        return providerDataMap;
    }
    
    /**
     * 데이터 초기화
     */
    public static void clear() {
        providerDataMap.clear();
    }
}

