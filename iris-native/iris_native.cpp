#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

static std::mutex mutex_;
static llama_model * model_ = nullptr;
static llama_context * ctx_ = nullptr;
static const llama_vocab * vocab_ = nullptr;

static void cleanup() {
    if (ctx_) { llama_free(ctx_); ctx_ = nullptr; }
    if (model_) { llama_model_free(model_); model_ = nullptr; }
    vocab_ = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_iris_assistant_IrisNeuralCore_nativeLoad(JNIEnv *env, jobject, jstring path) {
    std::lock_guard<std::mutex> guard(mutex_);
    cleanup();
    const char * filename = env->GetStringUTFChars(path, nullptr);
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    model_ = llama_model_load_from_file(filename, mp);
    env->ReleaseStringUTFChars(path, filename);
    if (!model_) return JNI_FALSE;
    vocab_ = llama_model_get_vocab(model_);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 512;
    cp.n_threads = 4;
    ctx_ = llama_init_from_model(model_, cp);
    if (!ctx_) { cleanup(); return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_iris_assistant_IrisNeuralCore_nativeGenerate(JNIEnv *env, jobject, jstring prompt, jint limit) {
    std::lock_guard<std::mutex> guard(mutex_);
    if (!ctx_ || !vocab_) return env->NewStringUTF("");
    const char *input = env->GetStringUTFChars(prompt, nullptr);
    std::string text = "<|im_start|>system\nVocê é Iris, uma assistente prestativa. Responda em português do Brasil. /no_think<|im_end|>\n<|im_start|>user\n";
    text += input;
    text += "<|im_end|>\n<|im_start|>assistant\n";
    env->ReleaseStringUTFChars(prompt, input);
    int n = -llama_tokenize(vocab_, text.c_str(), (int)text.size(), nullptr, 0, true, true);
    if (n <= 0 || n > 1600) return env->NewStringUTF("");
    std::vector<llama_token> tokens(n);
    if (llama_tokenize(vocab_, text.c_str(), (int)text.size(), tokens.data(), n, true, true) < 0)
        return env->NewStringUTF("");
    llama_memory_clear(llama_get_memory(ctx_), true);
    llama_batch batch = llama_batch_init(512, 0, 1);
    int offset = 0;
    while (offset < n) {
        batch.n_tokens = 0;
        int chunk = std::min(512, n - offset);
        for (int i=0; i<chunk; ++i) {
            int j = batch.n_tokens++;
            batch.token[j] = tokens[offset+i];
            batch.pos[j] = offset+i;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (offset+i == n-1);
        }
        if (llama_decode(ctx_, batch) != 0) { llama_batch_free(batch); return env->NewStringUTF(""); }
        offset += chunk;
    }
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));
    std::string output;
    for (int i=0; i<std::min((int)limit, 384); ++i) {
        llama_token tok = llama_sampler_sample(sampler, ctx_, -1);
        if (llama_vocab_is_eog(vocab_, tok)) break;
        char piece[512];
        int count = llama_token_to_piece(vocab_, tok, piece, sizeof(piece), 0, true);
        if (count > 0) output.append(piece, count);
        if (output.find("<|im_end|>") != std::string::npos) break;
        batch.n_tokens = 1;
        batch.token[0] = tok;
        batch.pos[0] = n+i;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        if (llama_decode(ctx_, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    llama_batch_free(batch);
    auto end = output.find("<|im_end|>");
    if (end != std::string::npos) output.resize(end);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_iris_assistant_IrisNeuralCore_nativeClose(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(mutex_);
    cleanup();
}
