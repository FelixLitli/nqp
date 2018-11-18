package org.perl6.nqp.truffle.nodes.call;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.perl6.nqp.truffle.nodes.NQPNode;
import org.perl6.nqp.truffle.nodes.NQPObjNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.perl6.nqp.truffle.sixmodel.TypeObject;
import com.oracle.truffle.api.object.DynamicObject;

import org.perl6.nqp.truffle.sixmodel.STable;
import org.perl6.nqp.truffle.sixmodel.reprs.P6opaqueObjectLayoutImpl;

import org.perl6.nqp.truffle.runtime.NQPArguments;
import org.perl6.nqp.truffle.runtime.NQPCodeRef;

import org.perl6.nqp.dsl.Deserializer;

@NodeInfo(shortName = "callmethod")
public final class NQPCallmethodNode extends NQPObjNode {
    public static final long NAMED = 1;
    public static final long FLAT = 2;

    private final FrameSlot contextSlot;

    @Child private NQPNode invocantNode;
    @Child private NQPNode methodNode;

    @CompilationFinal(dimensions = 1)
    private final long[] argumentFlags;

    @CompilationFinal(dimensions = 1)
    private final String[] argumentNames;

    @Children private final NQPNode[] argumentNodes;

    @Deserializer
    public NQPCallmethodNode(FrameSlot contextSlot, NQPNode invocantNode, NQPNode methodNode, long[] argumentFlags, String[] argumentNames, NQPNode[] argumentNodes) {
        this.contextSlot = contextSlot;
        this.invocantNode = invocantNode;
        this.methodNode = methodNode;
        this.argumentFlags = argumentFlags;
        this.argumentNames = argumentNames;
        this.argumentNodes = argumentNodes;
    }

    STable getStable(Object invocant) {
        if (P6opaqueObjectLayoutImpl.INSTANCE.isP6opaqueObject(invocant)) {
            return P6opaqueObjectLayoutImpl.INSTANCE.getStable((DynamicObject) invocant);
        } else if (invocant instanceof TypeObject) {
            return ((TypeObject) invocant).stable;
        } else {
            throw new RuntimeException("callmethod on: , can't get Stable" + invocant.getClass().getName());
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object invocant = invocantNode.execute(frame);
        String method = this.methodNode.executeStr(frame);

        System.out.println("calling method: " + method);

        /* TODO - specialization and all the cool inline caching */

        STable stable = getStable(invocant);

        Object foundMethod = stable.methodCache.get(method);

        if (foundMethod != null) {
            Object[] arguments = NQPArguments.unpack(frame, contextSlot, 1, argumentFlags, argumentNames, argumentNodes);
            NQPArguments.setUserArgument(arguments, 0, invocant);

            NQPCodeRef function = (NQPCodeRef) foundMethod;
            NQPArguments.setOuterFrame(arguments, function.getOuterFrame());
            IndirectCallNode callNode = IndirectCallNode.create();
            return callNode.call(function.getCallTarget(), arguments);
        } else {
            System.out.println("Can't find method" + method);
            return org.perl6.nqp.truffle.runtime.NQPNull.SINGLETON;
        }
    }
}
