package Transpiler;

public class FnameGenerator {
    private String bigAlphabetString = "abcdefghijklmnopqrstuvwxyz".toUpperCase();
    private char[] bigAlphabetArray = bigAlphabetString.toCharArray();
    private int currentIndex = 0;
    private int amountOfRounds = 0;
    private boolean invertedAlphabetCaps = false;

    public FnameGenerator(){

    }

    public String generateFunctionName() throws Exception{
        String fName = "";
        if (amountOfRounds > 0) {
            fName += bigAlphabetArray[amountOfRounds];
            fName += bigAlphabetArray[currentIndex];

            prepareNextUse();
        }
        else{
            fName += bigAlphabetArray[currentIndex];
            prepareNextUse();
        }

        return fName;
    }

    private void prepareNextUse() throws Exception{
        currentIndex++;  // INCREMENT FIRST!

        if (currentIndex >= bigAlphabetArray.length) {
            currentIndex = 0;
            if (amountOfRounds == (bigAlphabetArray.length - 1)) {
                bigAlphabetArray = bigAlphabetString.toLowerCase().toCharArray();
                amountOfRounds = 0;
                invertedAlphabetCaps = true;
            }
            else if(amountOfRounds == (bigAlphabetArray.length - 1) && invertedAlphabetCaps){
                throw new Exception("[ERROR] Transpiler has run out of function names!");
            }
            else{
                amountOfRounds++;
            }
        }
    }
}
