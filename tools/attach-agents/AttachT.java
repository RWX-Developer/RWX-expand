
import com.sun.tools.attach.VirtualMachine;
public class AttachT {
    public static void main(String[] a) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(a[0]);
        vm.loadAgent(a[1], a.length > 2 ? a[2] : "-");
        vm.detach();
        System.out.println("loaded");
    }
}
