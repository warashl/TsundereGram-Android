// Compatibility shim for the bundled WebRTC sources (voip/webrtc).
// Newer Abseil removed the absl::Nonnull<T> / absl::Nullable<T> /
// absl::NullabilityUnknown<T> alias templates in favour of the
// absl_nonnull / absl_nullable / absl_nullability_unknown macros.
// WebRTC here still uses the old templates, so provide equivalent no-op
// aliases. Force-included for C++ sources from jni/CMakeLists.txt.
#ifndef TMESSAGES_ABSL_NULLABILITY_COMPAT_H_
#define TMESSAGES_ABSL_NULLABILITY_COMPAT_H_

#ifdef __cplusplus
namespace absl {
template <typename T>
using Nonnull = T;
template <typename T>
using Nullable = T;
template <typename T>
using NullabilityUnknown = T;
}  // namespace absl
#endif  // __cplusplus

#endif  // TMESSAGES_ABSL_NULLABILITY_COMPAT_H_
