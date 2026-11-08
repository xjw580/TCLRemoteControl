package club.xiaojiawei.tclremotecontrol;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Sends one bounded frame off the UI thread; never queues a backlog of key presses. */
public final class IrController implements AutoCloseable {
    public interface Listener { void onError(String message); }
    private final ConsumerIrManager emitter;
    private final HandlerThread thread=new HandlerThread("RemoteInfrared");
    private final Handler worker, main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicInteger generation=new AtomicInteger();
    private final Listener listener;
    private final boolean available;
    private volatile boolean closed;
    public IrController(Context context,Listener listener) {
        this.listener=listener;
        emitter=context.getApplicationContext().getSystemService(ConsumerIrManager.class);
        boolean supported=false;
        try {
            if(emitter!=null && emitter.hasIrEmitter()) {
                ConsumerIrManager.CarrierFrequencyRange[] ranges=emitter.getCarrierFrequencies();
                if(ranges!=null) for(ConsumerIrManager.CarrierFrequencyRange range:ranges)
                    supported |= range.getMinFrequency()<=IrSignal.FREQUENCY && range.getMaxFrequency()>=IrSignal.FREQUENCY;
            }
        } catch(RuntimeException error) { Log.w("RemoteIR","Cannot query infrared emitter",error); }
        available=supported;
        thread.start(); worker=new Handler(thread.getLooper());
    }
    public boolean isAvailable() { return available && !closed; }
    public boolean send(RemoteKey key) {
        int command=IrSignal.command(key);
        if(!isAvailable() || command<0 || !busy.compareAndSet(false,true)) return false;
        int token=generation.get();
        boolean posted=worker.post(()->{
            try {
                if(closed || token!=generation.get()) return;
                emitter.transmit(IrSignal.FREQUENCY,IrSignal.pattern(command));
                Log.i("RemoteIR","Sent "+key.id+" command="+Integer.toHexString(command));
            } catch(RuntimeException error) {
                Log.w("RemoteIR","Infrared transmission failed",error);
                main.post(()->{ if(!closed && token==generation.get()) listener.onError("红外发送失败，请稍后重试"); });
            } finally { busy.set(false); }
        });
        if(!posted) busy.set(false);
        return posted;
    }
    public void cancel() { generation.incrementAndGet(); }
    @Override public void close() {
        closed=true; cancel(); main.removeCallbacksAndMessages(null); thread.quitSafely();
    }
}
