package com.example.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
@Slf4j
public class BookCoverStorageService {

    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.prefix:bookcovers/}")
    private String prefix;

    @Value("${app.s3.region:ap-southeast-1}")
    private String region;

    public BookCoverStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * imageUrl(원본 이미지 URL)을 받아서 S3에 업로드하고
     * 업로드된 S3 URL을 반환한다.
     */
    public String saveCoverFromUrl(String imageUrl, Long bookId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("imageUrl이 비어있음 → 업로드 스킵: bookId={}", bookId);
            return null;
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");

            //  리다이렉트(302 등) 허용
            conn.setInstanceFollowRedirects(true);

            //  타임아웃 조금 여유 있게
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(15000);

            //  봇 차단(403) 회피용 헤더
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Connection", "close");

            int status = conn.getResponseCode();
            String contentType = conn.getContentType();

            log.info("이미지 URL 응답: status={}, contentType={}, url={}", status, contentType, imageUrl);

            //  200~399 허용 (리다이렉트 포함)
            if (status < 200 || status >= 400) {
                log.warn("유효하지 않은 상태코드로 저장 중단: status={}, url={}", status, imageUrl);
                return null;
            }

            //  차단 페이지(HTML) 내려오는 경우 방지
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                log.warn("이미지 콘텐츠가 아님(차단/HTML 가능성): contentType={}, url={}", contentType, imageUrl);
                return null;
            }

            byte[] bytes;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                in.transferTo(bos);
                bytes = bos.toByteArray();
            }

            //  content-type 기반 확장자 결정
            String ext = guessExt(contentType);

            // S3 저장 key (폴더처럼 보이게 prefix 사용)
            String key = prefix + bookId + "." + ext;

            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType) //  실제 content-type 저장
                    .build();

            s3Client.putObject(putReq, RequestBody.fromBytes(bytes));
            log.info("S3 업로드 완료: s3://{}/{}", bucket, key);

            // S3 URL 반환(버킷이 public 읽기 가능해야 브라우저에서 직접 열림)
            String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
            log.info("S3 public URL: {}", publicUrl);

            return publicUrl;

        } catch (Exception e) {
            log.error("S3 업로드 실패: bookId={}, url={}, err={}", bookId, imageUrl, e.toString());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String guessExt(String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("png")) return "png";
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
        if (ct.contains("webp")) return "webp";
        if (ct.contains("gif")) return "gif";
        return "png";
    }
}
