# Zolsi-Mod

Client-side Minecraft utility client. In-game Dear ImGui menu (INSERT). Double click the generated Zolsi-Mod.jar file and attaches to a running Fabric MC session via Java Attach API. Features: ESP, Nametags, ArrayList, Triggerbot, AimAssist, CritAssist, Sprint, NoJumpDelay, AutoJumpReset, AntiBot and streamproof. No authentication, no server, no telemetry. Configs saved to `%APPDATA%\zolsi.cc\configs\`.

## Pictures
<img src="https://i.ibb.co/MFNVThR/image.png" width="600">
<img src="https://i.ibb.co/BVRHR0dt/image.png" width="600">
<img src="https://i.ibb.co/6JN1zgBq/image.png" width="600">
<img src="https://i.ibb.co/S7JdqHgV/image.png" width="600">
<img src="https://i.ibb.co/Pv2xD3Vv/image.png" width="600">


## Build

```
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.2
.\gradlew.bat build
```

Output: `build/libs/Zolsi-Mod.jar` <sub>double-click to inject.</sub>

## Requirements

- Minecraft 26.2 (Fabric Loader 0.19.3+)
- OpenGL graphics mode (Vulkan unsupported)
- Java 25
- Windows (HWND-based streamproof overlay)

## Fonts

- **Geist** by Vercel - SIL Open Font License 1.1
- **JetBrains Mono** by JetBrains - SIL Open Font License 1.1
- **imgui-java** (SpaiR) - Apache 2.0, bundled under `META-INF/jars/`

## License

[MIT](https://github.com/dcbzpass/Zolsi-Mod/blob/main/LICENSE)
