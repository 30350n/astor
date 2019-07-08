package sleepy;



public class sleep_test_2 {
    @org.junit.Test(timeout = 2000)
    public void test_0() throws java.lang.Exception {
        int result = sleep.main(0,false);
        org.junit.Assert.assertEquals( (int) 0, result);
    }
}