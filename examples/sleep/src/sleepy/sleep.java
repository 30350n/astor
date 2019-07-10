package sleepy;


class sleep {

    public static int main(int i,boolean b) {
        if (b) {
            try {
                Thread.sleep(1000);
                return 0;
            } catch (Exception e) {
                //TODO: handle exception
            }
            
        }
        int a = 2+3;
        a = 2+3;
        a = 2+3;
        a = 2+3;
        a = 2+3;
        a = 2+3;
        a = 2+3;
        if (i > 0){ //error should be i < 0
            return -1;
        }
        if (i > 0){
            return 1;
        }
        if (i == 0){
            return 0;
        }



        return -2;

    }
}