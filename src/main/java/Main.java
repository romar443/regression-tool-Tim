public class Main {
    public static void main(String[] args) {

        Test test = new Test();
//        Recipe recipe = new Recipe("food recipe");
        Recipe recipe = test.createRecipeWithCategory("recipe title", "category title");
        test.addCategoryToRecipe(recipe, "some title");
//        Category category = new Category("food category");
        System.out.println("Main finished");
    }
}
