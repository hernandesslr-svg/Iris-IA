use std::ffi::{c_char, CStr};
use std::sync::Mutex;
use crate::engine::{Engine, Options};

static ENGINE: Mutex<Option<Engine>> = Mutex::new(None);

#[no_mangle]
pub extern "C" fn iris_ines_init(model_path: *const c_char) -> i32 {
    if model_path.is_null() { return -1; }
    let path = unsafe { CStr::from_ptr(model_path) };
    let Ok(path) = path.to_str() else { return -2 };
    match Engine::load(path) {
        Ok(engine) => { *ENGINE.lock().unwrap_or_else(|e| e.into_inner()) = Some(engine); 0 }
        Err(_) => -3
    }
}

#[no_mangle]
pub extern "C" fn iris_ines_synthesize(
    text: *const c_char, wav_path: *const c_char
) -> i32 {
    if text.is_null() || wav_path.is_null() { return -1; }
    let (Ok(text), Ok(path)) = (
        unsafe { CStr::from_ptr(text) }.to_str(),
        unsafe { CStr::from_ptr(wav_path) }.to_str(),
    ) else { return -2 };
    let mut guard = ENGINE.lock().unwrap_or_else(|e| e.into_inner());
    let Some(engine) = guard.as_mut() else { return -3 };
    let Ok(voice) = engine.voice("ines") else { return -4 };
    let Ok(audio) = engine.synthesize(text, &voice, &Options::default()) else { return -5 };
    if audio.save_wav(path).is_err() { return -6; }
    0
}

#[no_mangle]
pub extern "C" fn iris_ines_close() {
    *ENGINE.lock().unwrap_or_else(|e| e.into_inner()) = None;
}

use jni::objects::{JClass, JString};
use jni::sys::jint;
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_iris_assistant_IrisInesNative_nativeInit(
    mut env: JNIEnv, _class: JClass, model: JString
) -> jint {
    let Ok(path) = env.get_string(&model) else { return -2 };
    let Ok(path) = std::ffi::CString::new(path.to_bytes()) else { return -2 };
    iris_ines_init(path.as_ptr())
}

#[no_mangle]
pub extern "system" fn Java_com_iris_assistant_IrisInesNative_nativeSynthesize(
    mut env: JNIEnv, _class: JClass, text: JString, wav: JString
) -> jint {
    let Ok(text) = env.get_string(&text) else { return -2 };
    let Ok(wav) = env.get_string(&wav) else { return -2 };
    let Ok(text) = std::ffi::CString::new(text.to_bytes()) else { return -2 };
    let Ok(wav) = std::ffi::CString::new(wav.to_bytes()) else { return -2 };
    iris_ines_synthesize(text.as_ptr(), wav.as_ptr())
}
