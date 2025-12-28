'use client';

import React, { useState, useEffect } from "react";
import api from "@/app/api/apiClient";

function Page() {
    const [imageUrl, setImageUrl] = useState("");

    const [postData, setPostData] = useState({
        title: "",
        description: "",
        content: "",
        categoryId: "",
        categoryName: "",
    });

    // ========================== 사용자 API Key 조회 ==========================
    const getUserApiKey = async () => {
        if (typeof window === "undefined") return null;

        try {
            const res = await api.get("/auth/user-info");

            const apiKey = res.headers["api-key"];
            if (!apiKey) {
                alert("등록된 API Key가 없습니다.");
                return null;
            }

            return apiKey;
        } catch (err) {
            console.error("API Key 조회 실패:", err);
            alert("API Key를 가져오지 못했습니다.");
            return null;
        }
    };

    // ========================== 이미지 생성 ==========================
    const imageGenerate = async (initialData = null) => {
        const currentData = initialData || postData;

        if (!currentData?.title) {
            alert("유효한 게시물 데이터가 없습니다.");
            return;
        }

        const apiKey = await getUserApiKey();
        if (!apiKey) return;

        const categoryPrompt =
            currentData.categoryName || currentData.categoryId || "기본 카테고리";

        const prompt =
            `제목: ${currentData.title}\n` +
            `설명: ${currentData.description}\n` +
            `위 내용을 기반으로 ${categoryPrompt} 카테고리에 어울리는 단일 책 표지 이미지를 생성.\n` +
            `빈 공간 없이 깔끔한 하드커버 스타일로 표현.`;

        try {
            setImageUrl("");
            alert("AI 이미지 생성 중입니다. 잠시 기다려주세요...");

            const res = await fetch(
                "https://api.openai.com/v1/images/generations",
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${apiKey}`,
                    },
                    body: JSON.stringify({
                        model: "dall-e-3",
                        prompt,
                        size: "1024x1024",
                        quality: "hd",
                    }),
                }
            );

            const result = await res.json();
            const url = result.data?.[0]?.url;

            if (!url) {
                console.error("OpenAI Error:", result.error);
                alert("이미지 생성 실패");
                return;
            }

            setImageUrl(url);
            alert("이미지 생성이 완료되었습니다.");
        } catch (err) {
            console.error("이미지 생성 오류:", err);
            alert("이미지 생성 중 오류가 발생했습니다.");
        }
    };

    // ========================== 자동 생성 ==========================
    useEffect(() => {
        const tempPostData = localStorage.getItem("temp_post_data");

        if (tempPostData) {
            const data = JSON.parse(tempPostData);
            setPostData(data);
            imageGenerate(data);
        } else {
            alert("게시물 정보를 찾을 수 없습니다.");
        }

        const handleMessage = (event) => {
            if (event.data?.imageUrl) {
                setImageUrl(event.data.imageUrl);
            }
        };

        window.addEventListener("message", handleMessage);
        return () => window.removeEventListener("message", handleMessage);
    }, []);

    // ========================== 결정 ==========================
    const handleDecision = () => {
        if (imageUrl && window.opener) {
            window.opener.postMessage({ imageUrl }, "*");
            window.close();
        } else {
            alert("생성된 이미지가 없습니다.");
        }
    };

    // ========================== UI ==========================
    return (
        <div style={{ maxWidth: "75%", margin: "0 auto", border: "1px solid black", padding: "10px", background: "white" }}>
            <div style={{ minHeight: "400px", border: "1px solid black", display: "flex", justifyContent: "center", alignItems: "center" }}>
                {imageUrl ? (
                    <img src={imageUrl} style={{ width: "100%", height: "auto" }} />
                ) : (
                    <div>이미지 생성 중...</div>
                )}
            </div>

            <div style={{ display: "flex", justifyContent: "space-between" }}>
                <button onClick={() => imageGenerate()}>재생성</button>
                <button onClick={handleDecision} disabled={!imageUrl}>
                    결정
                </button>
            </div>
        </div>
    );
}

export default Page;
