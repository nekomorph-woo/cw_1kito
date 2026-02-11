Request:
curl --location 'https://api.siliconflow.cn/v1/chat/completions' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer YOUR_API_KEY' \
--data '{
    "model": "zai-org/GLM-4.6V",
    "messages": [
      {
        "role": "user",
        "content": [
          {"type": "text", "text": "What'\''s in this image?"},
          {
            "type": "image_url",
            "image_url": {
                "url": "https://sf-maas-uat-prod.oss-cn-shanghai.aliyuncs.com/suggestion/lbygavkzjykewmmpnzfutkvedlowunms.png"
            }
          }
        ]
    }
    ],
    "temperature": 0.7,
    "max_tokens": 1000
  }'


---

messages.content.image_url.url
stringrequired
Either a URL of the image or the base64 encoded image data. For model deepseek-ai/DeepSeek-OCR, PDF files are also supported via URL or base64; other models accept images only. TeleAI/TeleMM only support the base64 encoded image data.

其他字段与LLM的大差不差

---

Response:

{
  "id": "019bda85c39aba6a5fccce598dac8587",
  "object": "chat.completion",
  "created": 1768897758,
  "model": "zai-org/GLM-4.6V",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "...",
        "reasoning_content": "..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 1383,
    "completion_tokens": 205,
    "total_tokens": 1588,
    "completion_tokens_details": {
      "reasoning_tokens": 118
    },
    "prompt_tokens_details": {
      "cached_tokens": 0
    },
    "prompt_cache_hit_tokens": 0,
    "prompt_cache_miss_tokens": 1383
  },
  "system_fingerprint": ""
}
