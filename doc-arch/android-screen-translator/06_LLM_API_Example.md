Request:

curl --request POST \
  --url https://api.siliconflow.cn/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "model": "Pro/zai-org/GLM-4.7",
    "messages": [
      {"role": "system", "content": "你是一个有用的助手"},
      {"role": "user", "content": "你好，请介绍一下你自己"}
    ]
  }'

---
以下字段虽然未在上面示例中展示，但是是实际可用的字段：
enable_thinking
boolean
Switches between thinking and non-thinking modes. This field supports the following models:

- Pro/zai-org/GLM-4.7
- deepseek-ai/DeepSeek-V3.2
- Pro/deepseek-ai/DeepSeek-V3.2
- zai-org/GLM-4.6
- Qwen/Qwen3-8B
- Qwen/Qwen3-14B
- Qwen/Qwen3-32B
- Qwen/Qwen3-30B-A3B
- tencent/Hunyuan-A13B-Instruct
- zai-org/GLM-4.5V
- deepseek-ai/DeepSeek-V3.1-Terminus
- Pro/deepseek-ai/DeepSeek-V3.1-Terminus


thinking_budget
integerdefault:4096
Maximum number of tokens for chain-of-thought output. This field applies to all Reasoning models.

Required range: 128 <= x <= 32768

temperature
number<float>
Determines the degree of randomness in the response.

Example:
0.7

---

Response:

{
  "id": "019bdaa55225ef854b320e9b838f77ce",
  "object": "chat.completion",
  "created": 1768899826,
  "model": "Pro/zai-org/GLM-4.7",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好！...",
        "reasoning_content": "..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 15,
    "completion_tokens": 1540,
    "total_tokens": 1555,
    "completion_tokens_details": {
      "reasoning_tokens": 1190
    },
    "prompt_tokens_details": {
      "cached_tokens": 0
    },
    "prompt_cache_hit_tokens": 0,
    "prompt_cache_miss_tokens": 15
  },
  "system_fingerprint": ""
}


