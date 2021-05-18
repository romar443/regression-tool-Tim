import java.util.List;

public class Test {

    public Test() {
        Recipe ice_cream = createRecipeWithCategory("Ice Cream", "sweet food");
        addCategoryToRecipe(ice_cream, "cold food");
    }

    public Recipe createRecipeWithCategory(String recipetitle, String categorytitle){
        Recipe recipe = new Recipe(recipetitle);
        Category category = new Category(categorytitle);
        recipe.addCategory(category);
        category.addCorrespondingRecipe(recipe);
        return recipe;
    }

    public Recipe addCategoryToRecipe(Recipe recipe, String categoryTitle){
        Category category = new Category(categoryTitle);
        category.addCorrespondingRecipe(recipe);
        recipe.addCategory(category);
        return recipe;
    }
}
