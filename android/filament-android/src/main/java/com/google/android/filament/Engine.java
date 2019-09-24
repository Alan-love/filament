/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.filament;

import android.support.annotation.NonNull;

import com.google.android.filament.proguard.UsedByReflection;

/**
 * Engine is filament's main entry-point.
 * <p>
 * An Engine instance main function is to keep track of all resources created by the user and
 * manage the rendering thread as well as the hardware renderer.
 * <p>
 * To use filament, an Engine instance must be created first:
 *
 * <pre>
 * import com.google.android.filament.*
 *
 * Engine engine = Engine.create();
 * </pre>
 * <p>
 * Engine essentially represents (or is associated to) a hardware context
 * (e.g. an OpenGL ES context).
 * <p>
 * Rendering typically happens in an operating system's window (which can be full screen), such
 * window is managed by a {@link Renderer}.
 * <p>
 * A typical filament render loop looks like this:
 *
 *
 * <pre>
 * import com.google.android.filament.*
 *
 * Engin engine         = Engine.create();
 * SwapChain swapChain  = engine.createSwapChain(nativeWindow);
 * Renderer renderer    = engine.createRenderer();
 * Scene scene          = engine.createScene();
 * View view            = engine.createView();
 *
 * view.setScene(scene);
 *
 * do {
 *     // typically we wait for VSYNC and user input events
 *     if (renderer.beginFrame(swapChain)) {
 *         renderer.render(view);
 *         renderer.endFrame();
 *     }
 * } while (!quit);
 *
 * engine.destroyView(view);
 * engine.destroyScene(scene);
 * engine.destroyRenderer(renderer);
 * engine.destroySwapChain(swapChain);
 * engine.destroy();
 * </pre>
 *
 * <h1><u>Resource Tracking</u></h1>
 * <p>
 * Each <code>Engine</code> instance keeps track of all objects created by the user, such as vertex
 * and index buffers, lights, cameras, etc...
 * The user is expected to free those resources, however, leaked resources are freed when the
 * engine instance is destroyed and a warning is emitted in the console.
 *
 * <h1><u>Thread safety</u></h1>
 * <p>
 * An <code>Engine</code> instance is not thread-safe. The implementation makes no attempt to
 * synchronize calls to an <code>Engine</code> instance methods.
 * If multi-threading is needed, synchronization must be external.
 *
 * <h1><u>Multi-threading</u></h1>
 * <p>
 * When created, the <code>Engine</code> instance starts a render thread as well as multiple worker
 * threads, these threads have an elevated priority appropriate for rendering, based on the
 * platform's best practices. The number of worker threads depends on the platform and is
 * automatically chosen for best performance.
 * <p>
 * On platforms with asymmetric cores (e.g. ARM's Big.Little), <code>Engine</code> makes some
 * educated guesses as to which cores to use for the render thread and worker threads. For example,
 * it'll try to keep an OpenGL ES thread on a Big core.
 *
 * <h1><u>Swap Chains</u></h1>
 * <p>
 * A swap chain represents an Operating System's <b>native</b> renderable surface.
 * Typically it's a window or a view. Because a {@link SwapChain} is initialized from a native
 * object, it is given to filament as an <code>Object</code>, which must be of the proper type for
 * each platform filament is running on.
 * <p>
 *
 * @see SwapChain
 * @see Renderer
 */
public class Engine {
    private long mNativeObject;
    @NonNull private final TransformManager mTransformManager;
    @NonNull private final LightManager mLightManager;
    @NonNull private final RenderableManager mRenderableManager;

    /**
     * Denotes a backend
     */
    public enum Backend {
        /**
         * Automatically selects an appropriate driver for the platform.
         */
        DEFAULT,
        /**
         * Selects the OpenGL ES driver.
         */
        OPENGL,
        /**
         * Selects the experimental Vulkan driver.
         */
        VULKAN,
        /**
         * Selects the no-op driver for testing purposes.
         */
        NOOP,
    }

    private Engine(long nativeEngine) {
        mNativeObject = nativeEngine;
        mTransformManager = new TransformManager(nGetTransformManager(nativeEngine));
        mLightManager = new LightManager(nGetLightManager(nativeEngine));
        mRenderableManager = new RenderableManager(nGetRenderableManager(nativeEngine));
    }

    /**
     * Creates an instance of Engine using the default {@link Backend}
     * <p>
     * This method is one of the few thread-safe methods.
     *
     * @return A newly created <code>Engine</code>, or <code>null</code> if the GPU driver couldn't
     *         be initialized, for instance if it doesn't support the right version of OpenGL or
     *         OpenGL ES.
     *
     * @exception IllegalStateException can be thrown if there isn't enough memory to
     * allocate the command buffer.
     *
     */
    @NonNull
    public static Engine create() {
        long nativeEngine = nCreateEngine(0, 0);
        if (nativeEngine == 0) throw new IllegalStateException("Couldn't create Engine");
        return new Engine(nativeEngine);
    }

    /**
     * Creates an instance of Engine using the specified {@link Backend}
     * <p>
     * This method is one of the few thread-safe methods.
     *
     * @param backend           driver backend to use
     *
     * @return A newly created <code>Engine</code>, or <code>null</code> if the GPU driver couldn't
     *         be initialized, for instance if it doesn't support the right version of OpenGL or
     *         OpenGL ES.
     *
     * @exception IllegalStateException can be thrown if there isn't enough memory to
     * allocate the command buffer.
     *
     */
    @NonNull
    public static Engine create(@NonNull Backend backend) {
        long nativeEngine = nCreateEngine(backend.ordinal(), 0);
        if (nativeEngine == 0) throw new IllegalStateException("Couldn't create Engine");
        return new Engine(nativeEngine);
    }

    /**
     * Creates an instance of Engine using the {@link Backend#OPENGL} and a shared OpenGL context.
     * <p>
     * This method is one of the few thread-safe methods.
     *
     * @param sharedContext  A platform-dependant OpenGL context used as a shared context
     *                       when creating filament's internal context. On Android this parameter
     *                       <b>must be</b> an instance of {@link android.opengl.EGLContext}.
     *
     * @return A newly created <code>Engine</code>, or <code>null</code> if the GPU driver couldn't
     *         be initialized, for instance if it doesn't support the right version of OpenGL or
     *         OpenGL ES.
     *
     * @exception IllegalStateException can be thrown if there isn't enough memory to
     * allocate the command buffer.
     *
     */
    @NonNull
    public static Engine create(@NonNull Object sharedContext) {
        if (Platform.get().validateSharedContext(sharedContext)) {
            long nativeEngine = nCreateEngine(0,
                    Platform.get().getSharedContextNativeHandle(sharedContext));
            if (nativeEngine == 0) throw new IllegalStateException("Couldn't create Engine");
            return new Engine(nativeEngine);
        }
        throw new IllegalArgumentException("Invalid shared context " + sharedContext);
    }

    /**
     * @return <code>true</code> if this <code>Engine</code> is initialized properly.
     */
    public boolean isValid() {
        return mNativeObject != 0;
    }

    /**
     * Destroy the <code>Engine</code> instance and all associated resources.
     * <p>
     * This method is one of the few thread-safe methods.
     * <p>
     * {@link Engine#destroy()} should be called last and after all other resources have been
     * destroyed, it ensures all filament resources are freed.
     * <p>
     * <code>Destroy</code> performs the following tasks:
     * <li>Destroy all internal software and hardware resources.</li>
     * <li>Free all user allocated resources that are not already destroyed and logs a warning.
     *     <p>This indicates a "leak" in the user's code.</li>
     * <li>Terminate the rendering engine's thread.</li>
     *
     * <pre>
     * Engine engine = Engine.create();
     * engine.destroy();
     * </pre>
     */
    public void destroy() {
        nDestroyEngine(getNativeObject());
        clearNativeObject();
    }

    /**
     * @return the backend used by this <code>Engine</code>
     */
    @NonNull
    public Backend getBackend() {
        return Backend.values()[(int) nGetBackend(getNativeObject())];
    }

    // SwapChain

    /**
     * Creates an opaque {@link SwapChain} from the given OS native window handle.
     *
     * @param surface on Android, <b>must be</b> an instance of {@link android.view.Surface}
     *
     * @return a newly created {@link SwapChain} object
     *
     * @exception IllegalStateException can be thrown if the SwapChain couldn't be created
     */
    @NonNull
    public SwapChain createSwapChain(@NonNull Object surface) {
        return createSwapChain(surface, SwapChain.CONFIG_DEFAULT);
    }

    /**
     * Creates a {@link SwapChain} from the given OS native window handle.
     *
     * @param surface on Android, <b>must be</b> an instance of {@link android.view.Surface}
     *
     * @param flags configuration flags, see {@link SwapChain}
     *
     * @return a newly created {@link SwapChain} object
     *
     * @exception IllegalStateException can be thrown if the SwapChain couldn't be created
     *
     * @see SwapChain#CONFIG_DEFAULT
     * @see SwapChain#CONFIG_TRANSPARENT
     * @see SwapChain#CONFIG_READABLE
     *
     */
    @NonNull
    public SwapChain createSwapChain(@NonNull Object surface, long flags) {
        if (Platform.get().validateSurface(surface)) {
            long nativeSwapChain = nCreateSwapChain(getNativeObject(), surface, flags);
            if (nativeSwapChain == 0) throw new IllegalStateException("Couldn't create SwapChain");
            return new SwapChain(nativeSwapChain, surface);
        }
        throw new IllegalArgumentException("Invalid surface " + surface);
    }

    /**
     * Creates a {@link SwapChain} from a {@link NativeSurface}.
     *
     * @param surface a properly initialized {@link NativeSurface}
     *
     * @param flags configuration flags, see {@link SwapChain}
     *
     * @return a newly created {@link SwapChain} object
     *
     * @exception IllegalStateException can be thrown if the SwapChain couldn't be created
     */
    @NonNull
    public SwapChain createSwapChainFromNativeSurface(@NonNull NativeSurface surface, long flags) {
        long nativeSwapChain =
                nCreateSwapChainFromRawPointer(getNativeObject(), surface.getNativeObject(), flags);
        if (nativeSwapChain == 0) throw new IllegalStateException("Couldn't create SwapChain");
        return new SwapChain(nativeSwapChain, surface);
    }

    public void destroySwapChain(@NonNull SwapChain swapChain) {
        nDestroySwapChain(getNativeObject(), swapChain.getNativeObject());
        swapChain.clearNativeObject();
    }

    // View

    @NonNull
    public View createView() {
        long nativeView = nCreateView(getNativeObject());
        if (nativeView == 0) throw new IllegalStateException("Couldn't create View");
        return new View(nativeView);
    }

    public void destroyView(@NonNull View view) {
        nDestroyView(getNativeObject(), view.getNativeObject());
        view.clearNativeObject();
    }

    // Renderer

    @NonNull
    public Renderer createRenderer() {
        long nativeRenderer = nCreateRenderer(getNativeObject());
        if (nativeRenderer == 0) throw new IllegalStateException("Couldn't create Renderer");
        return new Renderer(this, nativeRenderer);
    }

    public void destroyRenderer(@NonNull Renderer renderer) {
        nDestroyRenderer(getNativeObject(), renderer.getNativeObject());
        renderer.clearNativeObject();
    }

    // Camera

    @NonNull
    public Camera createCamera() {
        long nativeCamera = nCreateCamera(getNativeObject());
        if (nativeCamera == 0) throw new IllegalStateException("Couldn't create Camera");
        return new Camera(nativeCamera);
    }

    @NonNull
    public Camera createCamera(@Entity int entity) {
        long nativeCamera = nCreateCameraWithEntity(getNativeObject(), entity);
        if (nativeCamera == 0) throw new IllegalStateException("Couldn't create Camera");
        return new Camera(nativeCamera);
    }

    public void destroyCamera(@NonNull Camera camera) {
        nDestroyCamera(getNativeObject(), camera.getNativeObject());
        camera.clearNativeObject();
    }

    // Scene

    @NonNull
    public Scene createScene() {
        long nativeScene = nCreateScene(getNativeObject());
        if (nativeScene == 0) throw new IllegalStateException("Couldn't create Scene");
        return new Scene(nativeScene);
    }

    public void destroyScene(@NonNull Scene scene) {
        nDestroyScene(getNativeObject(), scene.getNativeObject());
        scene.clearNativeObject();
    }

    // Stream

    public void destroyStream(@NonNull Stream stream) {
        nDestroyStream(getNativeObject(), stream.getNativeObject());
        stream.clearNativeObject();
    }

    // Fence

    @NonNull
    public Fence createFence() {
        long nativeFence = nCreateFence(getNativeObject());
        if (nativeFence == 0) throw new IllegalStateException("Couldn't create Fence");
        return new Fence(nativeFence);
    }

    public void destroyFence(@NonNull Fence fence) {
        nDestroyFence(getNativeObject(), fence.getNativeObject());
        fence.clearNativeObject();
    }

    // others...

    public void destroyIndexBuffer(@NonNull IndexBuffer indexBuffer) {
        nDestroyIndexBuffer(getNativeObject(), indexBuffer.getNativeObject());
        indexBuffer.clearNativeObject();
    }

    public void destroyVertexBuffer(@NonNull VertexBuffer vertexBuffer) {
        nDestroyVertexBuffer(getNativeObject(), vertexBuffer.getNativeObject());
        vertexBuffer.clearNativeObject();
    }

    public void destroyIndirectLight(@NonNull IndirectLight ibl) {
        nDestroyIndirectLight(getNativeObject(), ibl.getNativeObject());
        ibl.clearNativeObject();
    }

    public void destroyMaterial(@NonNull Material material) {
        nDestroyMaterial(getNativeObject(), material.getNativeObject());
        material.clearNativeObject();
    }

    public void destroyMaterialInstance(@NonNull MaterialInstance materialInstance) {
        nDestroyMaterialInstance(getNativeObject(), materialInstance.getNativeObject());
        materialInstance.clearNativeObject();
    }

    public void destroySkybox(@NonNull Skybox skybox) {
        nDestroySkybox(getNativeObject(), skybox.getNativeObject());
        skybox.clearNativeObject();
    }

    public void destroyTexture(@NonNull Texture texture) {
        nDestroyTexture(getNativeObject(), texture.getNativeObject());
        texture.clearNativeObject();
    }

    public void destroyRenderTarget(@NonNull RenderTarget target) {
        nDestroyRenderTarget(getNativeObject(), target.getNativeObject());
        target.clearNativeObject();
    }

    public void destroyEntity(@Entity int entity) {
        nDestroyEntity(getNativeObject(), entity);
    }

    // Managers

    @NonNull
    public TransformManager getTransformManager() {
        return mTransformManager;
    }

    @NonNull
    public LightManager getLightManager() {
        return mLightManager;
    }

    @NonNull
    public RenderableManager getRenderableManager() {
        return mRenderableManager;
    }

    public void flushAndWait() {
        Fence.waitAndDestroy(createFence(), Fence.Mode.FLUSH);
    }

    @UsedByReflection("TextureHelper.java")
    public long getNativeObject() {
        if (mNativeObject == 0) {
            throw new IllegalStateException("Calling method on destroyed Engine");
        }
        return mNativeObject;
    }

    private void clearNativeObject() {
        mNativeObject = 0;
    }

    private static native long nCreateEngine(long backend, long sharedContext);
    private static native void nDestroyEngine(long nativeEngine);
    private static native long nGetBackend(long nativeEngine);
    private static native long nCreateSwapChain(long nativeEngine, Object nativeWindow, long flags);
    private static native long nCreateSwapChainFromRawPointer(long nativeEngine, long pointer, long flags);
    private static native void nDestroySwapChain(long nativeEngine, long nativeSwapChain);
    private static native long nCreateView(long nativeEngine);
    private static native void nDestroyView(long nativeEngine, long nativeView);
    private static native long nCreateRenderer(long nativeEngine);
    private static native void nDestroyRenderer(long nativeEngine, long nativeRenderer);
    private static native long nCreateCamera(long nativeEngine);
    private static native long nCreateCameraWithEntity(long nativeEngine, int entity);
    private static native void nDestroyCamera(long nativeEngine, long nativeCamera);
    private static native long nCreateScene(long nativeEngine);
    private static native void nDestroyScene(long nativeEngine, long nativeScene);
    private static native long nCreateFence(long nativeEngine);
    private static native void nDestroyFence(long nativeEngine, long nativeFence);
    private static native void nDestroyStream(long nativeEngine, long nativeStream);
    private static native void nDestroyIndexBuffer(long nativeEngine, long nativeIndexBuffer);
    private static native void nDestroyVertexBuffer(long nativeEngine, long nativeVertexBuffer);
    private static native void nDestroyIndirectLight(long nativeEngine, long nativeIndirectLight);
    private static native void nDestroyMaterial(long nativeEngine, long nativeMaterial);
    private static native void nDestroyMaterialInstance(long nativeEngine, long nativeMaterialInstance);
    private static native void nDestroySkybox(long nativeEngine, long nativeSkybox);
    private static native void nDestroyTexture(long nativeEngine, long nativeTexture);
    private static native void nDestroyRenderTarget(long nativeEngine, long nativeTarget);
    private static native void nDestroyEntity(long nativeEngine, int entity);
    private static native long nGetTransformManager(long nativeEngine);
    private static native long nGetLightManager(long nativeEngine);
    private static native long nGetRenderableManager(long nativeEngine);
}
