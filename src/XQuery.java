import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import java.util.Iterator;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class XQuery {
    public static void main(String[] args) {
        try {

            String query1 = "for $s in doc(\"j_caesar.xml\")//SPEAKER, $a in doc(\"j_caesar.xml\")//ACT,\n" +
                    "    $sp in $a//SPEAKER, $stxt in $s/text()\n" +
                    "where $sp eq $s and $stxt = \"CAESAR\"\n" +
                    "return <act> { $a/TITLE/text()} </act>\n";
            String query2 = "for $tuple in join(\n" +
                    "\tfor $a in doc(\"j_caesar.xml\")//ACT,\n" +
                    "\t$sp in $a//SPEAKER\n" +
                    "\treturn <tuple>{\n" +
                    "\t<a>{$a}</a>,\n" +
                    "\t<sp>{$sp}</sp>\n" +
                    "\t}</tuple>,\n" +
                    "\tfor $s in doc(\"j_caesar.xml\")//SPEAKER,\n" +
                    "\t$stxt in $s/text()\n" +
                    "\twhere $stxt = \"CAESAR\"\n" +
                    "\treturn <tuple>{\n" +
                    "\t<s>{$s}</s>,\n" +
                    "\t<stxt>{$stxt}</stxt>\n" +
                    "\t}</tuple>,\n" +
                    "\t[sp],[s])\n" +
                    "return <act>{\n" +
                    "\t($tuple/a/*/TITLE/text())\n" +
                    "\t}</act>";
            ANTLRInputStream input = new ANTLRInputStream(query1);
            XQueryLexer lexer = new XQueryLexer(input);

//            XPathLexer lexer = new XPathLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            XQueryParser parser = new XQueryParser(tokens);
//            XPathParser parser = new XPathParser(tokens);
            parser.removeParseListeners();

            ParseTree tree = parser.xq();
            EvalXQuery eva = new EvalXQuery();
            long startTime = System.currentTimeMillis();
//            ArrayList<Node> rstNodes = eva.visit(tree);
//            long totalTime = System.currentTimeMillis() - startTime;
//
//            File writename = new File("output.txt"); // rp
//            writename.createNewFile(); // create
//
//            BufferedWriter out = new BufferedWriter(new FileWriter(writename));
//
//            for (int i = 0; i < rstNodes.size(); i++) {
//                System.out.println(output(rstNodes.get(i), 0));
//                out.write(output(rstNodes.get(i), 0));
//            }
//            out.flush(); // out
//            out.close(); // close
//            System.out.print("Original version time:");
//            System.out.println(totalTime);
            String rewriteXq = rewrite(tree);
//            System.out.println(rewriteXq);

            File rewriteFile = new File("rewrite.txt"); // rp

            if(!rewriteXq.isEmpty()) {
                try {
                    BufferedWriter rewriteOut = new BufferedWriter(new FileWriter(rewriteFile));
                    rewriteOut.write(rewriteXq);
                    rewriteOut.close();
                }
                catch (IOException e)
                {
                    System.out.println("Exception ");
                }
                input = new ANTLRInputStream(rewriteXq);
                lexer = new XQueryLexer(input);
                tokens = new CommonTokenStream(lexer);
                parser = new XQueryParser(tokens);
                tree = parser.xq();
                ArrayList<Node> rst = eva.visit(tree);
                File writename2 = new File("output2.txt"); // rp
                writename2.createNewFile(); // create

                BufferedWriter out2 = new BufferedWriter(new FileWriter(writename2));
                System.out.println("the size is "+rst.size());
                for (int i = 0; i < rst.size(); i++) {
                    System.out.println(output(rst.get(i), 3));
                    out2.write(output(rst.get(i), 0));
                }
                out2.flush(); // out
                out2.close(); // close
//                System.out.print("Original version time:");
//                System.out.println(totalTime);
            } else {
                System.out.println("No need to rewrite the query!");
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    public static String output(Node node, int indentNumber) {
        if(node instanceof Attr) {       //check type
            return node.getNodeName() + "=" + node.getNodeValue();
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        if(indentNumber > 0) {
            transformerFactory.setAttribute("indent-number", indentNumber);
        }
        Transformer transformer = null;
        try {
            transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString();
        } catch (TransformerConfigurationException e) {
            System.err.println(e);
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static String rewrite(ParseTree tree){
        String result = "for $tuple in ";
        System.out.println(tree.toString());
        ParseTree xq = tree;
        String resultTag = null;
        if (!xq.getChild(0).getText().contains("for")){
            resultTag = xq.getChild(1).getText();
            for (int i=0; i<xq.getChildCount(); ++i){
                if (xq.getChild(i).getText().contains("for")){
                    xq = xq.getChild(i);
                }
            }
        }
        ParseTree forClause = xq.getChild(0);
        ParseTree whereClause = xq.getChild(1);
        ParseTree returnClause = xq.getChild(2);
        LinkedHashMap<String, LinkedHashMap<String, String>> varMap = new LinkedHashMap<>();
        ArrayList<LinkedHashMap<String, String>> partitions = new ArrayList<>();
        ArrayList<String> whereString = new ArrayList<>();

        for (int i=0; i<forClause.getChildCount(); ++i){
            ParseTree cur = forClause.getChild(i);
            String curText = cur.getText();
            if (curText.matches("^\\$\\w*$")){
                String var = cur.getChild(0).getText() + cur.getChild(1).getText();
                xq = forClause.getChild(i+2); // for var in xq, var has children $ and a
                if (xq.getText().contains("document") || xq.getText().contains("doc")){
                    LinkedHashMap<String, String> partition = new LinkedHashMap<>();
                    partition.put(var, xq.getText());
                    partitions.add(partition);
                    varMap.put(var, partition);
                    whereString.add("");
                } else {
                    for (LinkedHashMap<String, String> p : partitions){
                        String pvar = xq.getChild(0).getChild(0).getText();
                        if(p.containsKey(pvar)){
                            p.put(var, xq.getText());
                            varMap.put(var, p);
                        }
                    }
                }
            }
        }
        //System.out.println("partitions size: " + partitions.size());
        if (partitions.size()<2){
            return "";
        }
        String left = "";
        boolean first = true;
        String condText = whereClause.getChild(1).getText();
        String[] condsArr = condText.split("and");
        ArrayList<String> conds = new ArrayList<>(Arrays.asList(condsArr));
        while (partitions.size()>1){
            ArrayList<String> leftBracket = new ArrayList<>();
            ArrayList<String> rightBracket = new ArrayList<>();
            ArrayList<String> curConds = new ArrayList<>(conds);
            for (int i=0; i<conds.size(); ++i){
                String c = conds.get(i);
                String[] condVars = c.split("eq|="); // amazing eq|=
                HashMap<String, String> p1 = varMap.get(condVars[0]);
                HashMap<String, String> p2 = varMap.get(condVars[1]);
                if (p1 == p2 || !condVars[0].contains("$") || !condVars[1].contains("$")) {
                    int ind = partitions.indexOf(p1);
                    if (whereString.get(ind).isEmpty()){
                        whereString.remove(ind);
                        whereString.add(ind, "where " + condVars[0] + " eq " + condVars[1] + " ");
                    } else {
                        String s = whereString.get(ind);
                        whereString.remove(ind);
                        whereString.add(ind, s + "and " + condVars[0] + " eq " + condVars[1] + " ");
                    }
                    curConds.remove(c);
                } else if (p1 != p2 && p1 != null && p2 != null && leftBracket.isEmpty()){
                    leftBracket.add(condVars[0]);
                    rightBracket.add(condVars[1]);
                    curConds.remove(c);
                } else if (p1 == varMap.get(leftBracket.get(0)) && p2 == varMap.get(rightBracket.get(0))){
                    leftBracket.add(condVars[0]);
                    rightBracket.add(condVars[1]);
                    curConds.remove(c);
                } else if (p1 == varMap.get(rightBracket.get(0)) && p2 == varMap.get(leftBracket.get(0))){
                    rightBracket.add(condVars[0]);
                    leftBracket.add(condVars[1]);
                    curConds.remove(c);
                }
            }
            conds = curConds;
            LinkedHashMap<String, String> p1 = varMap.get(leftBracket.get(0));
            LinkedHashMap<String, String> p2 = varMap.get(rightBracket.get(0));
            if (first){
                first = false;
                left += "for ";
                for (String var : p1.keySet()){
                    left += var + " in " + p1.get(var) + ", ";
                }
                left = left.substring(0, left.length()-2);
                String where = whereString.get(partitions.indexOf(p1));
                if (!where.equals("")){
                    left += "\n\t" + where;
                }
                left += "\n\treturn <tuple>{";
                for (String var : p1.keySet()){
                    left += "<" + var.substring(1, var.length()) + ">{" + var + "}"
                            + "</" + var.substring(1, var.length()) + ">,";
                }
                left = left.substring(0, left.length()-1) + "}</tuple>,";
            }
            left = rewriteToJoin(left, p2, leftBracket, rightBracket, whereString, partitions);

            for (Map.Entry<String, String> entry : p2.entrySet()){
                p1.put(entry.getKey(), entry.getValue());
                varMap.replace(entry.getKey(), p1);
            }
            whereString.remove(partitions.indexOf(p2));
            partitions.remove(p2);
        }
        result += left.substring(0, left.length()-1);
        String returnText = returnClause.getChild(1).getText();
        Pattern pattern = Pattern.compile("\\$\\w+\\d*\\/text\\(\\)");
        Matcher matcher = pattern.matcher(returnText);
        while (matcher.find()){
            String Mat = matcher.group(0);
            String newMat = Mat.replace("/", "//");
            returnText = returnText.replace(Mat, newMat);
        }
        pattern = Pattern.compile("\\$\\w+\\d*\\/[^\\/]");
        matcher = pattern.matcher(returnText);
        while (matcher.find()){
//			returnText = returnText.replace(matcher.group(0), matcher.group(0) + "/*");

            String Mat =  matcher.group(0);
            int Matlen = Mat.length();
            if (matcher.group(0).contains("/")){
                returnText = returnText.replace(Mat, Mat.substring(0, Matlen-2) + "/*"+Mat.substring(Matlen-2, Matlen));
            }
        }
        pattern = Pattern.compile("\\$\\w+\\d*[,|}| |\\t]");
        matcher = pattern.matcher(returnText);
        while (matcher.find()){
            String Mat = matcher.group(0);
            int Matlen = Mat.length();
            returnText = returnText.replace(Mat, Mat.substring(0, Matlen-1) + "/*"+Mat.substring(Matlen-1, Matlen));
        }

        result += "\nreturn " + returnText.replace("$", "$tuple/");
        if (resultTag != null){
            result = "<" + resultTag + ">{\n" + result + "\n}</" + resultTag + ">";
        }
        return result;
    }

    private static String rewriteToJoin(String left, LinkedHashMap<String, String> p2,
                                        ArrayList<String> leftBracket, ArrayList<String> rightBracket,
                                        ArrayList<String> whereString, ArrayList<LinkedHashMap<String, String>> partitions){
        left = "join( " + left;
        left += "\n\tfor ";
        for (String var : p2.keySet()){
            left += var + " in " + p2.get(var) + ", ";
        }
        left = left.substring(0, left.length()-2);
        String where = whereString.get(partitions.indexOf(p2));
        if (!where.equals("")){
            left += "\n\t" + where;
        }
        left += "\n\treturn <tuple>{";
        for (String var : p2.keySet()){
            left += "<" + var.substring(1, var.length()) + ">{" + var + "}"
                    + "</" + var.substring(1, var.length()) + ">,";
        }
        left = left.substring(0, left.length()-1) + "}</tuple>,";
        left += "\n\t[";
        for (String var : leftBracket){
            left += var.substring(1, var.length()) + ",";
        }
        left = left.substring(0, left.length()-1) + "],[";
        for (String var : rightBracket){
            left += var.substring(1, var.length()) + ",";
        }
        left = left.substring(0, left.length()-1) + "]),";
        return left;
    }
//    private static String rewrite(ParseTree tree){
//        String res = "for $tuple in ";
////        System.out.println(tree.toString());
//        String rstTag = null;
//        ParseTree xq = null;
//        if(tree.getChild(0).getText().contains("<")){
//            rstTag = tree.getChild(1).getText();
////            System.out.println(tree.getChild(1).getText());
////            System.out.println(rstTag);
//            xq = tree.getChild(4);
////            System.out.println("xq " + xq);
//
//        }
//        if(xq == null) {
//            xq = tree;
//        }
//
//        ParseTree forClause = null;
//        ParseTree whereClause = null;
//        ParseTree returnClause = null;
//
//        for (int i = 0; i < xq.getChildCount(); i++) {
//            if(xq.getChild(i).getText().contains("for"))
//                forClause = xq.getChild(i);
//            else if (xq.getChild(i).getText().contains("return"))
//                returnClause = xq.getChild(i);
//            else if (xq.getChild(i).getText().contains("where"))
//                whereClause = xq.getChild(i);
//
//        }
//        if(forClause == null || whereClause == null)
//            return "";
//
//        //var - belonging parts
//        HashMap<String, LinkedHashMap<String, String>> varMaps = new HashMap<>();
//
//        // different join parts, var - path
//        ArrayList<LinkedHashMap<String, String>> joinParts = new ArrayList<>();
//        ArrayList<String> whereCond = new ArrayList<>();
////
//        for (int i = 0;i < forClause.getChildCount();i++) {
//            ParseTree cur = forClause.getChild(i);
//            String cText = cur.getText();
////            System.out.println(cText);
//            if (cText.contains("$")) {
//                String var = cText;
////                System.out.println("var:" + var);
//                ParseTree tmp = forClause.getChild(i + 2);
//                if (tmp != null && (tmp.getText().contains("document") || tmp.getText().contains("doc"))) {
////                    System.out.println("find new join parts: " + tmp.getText());
//                    LinkedHashMap<String, String> parts = new LinkedHashMap<>();
//                    parts.put(var, tmp.getText());
//                    joinParts.add(parts);
//                    varMaps.put(var, parts);
//                    whereCond.add("");
//                } else if (tmp != null) {
//                    for (LinkedHashMap<String, String> p : joinParts) {
//                        String pVar = tmp.getChild(0).getText();
//                        if (p.containsKey(pVar)) {
////                            System.out.println("patent var:" + pVar);
//                            p.put(var, tmp.getText());
//                            varMaps.put(var, p);
//                        }
//                    }
//                }
//
//            }
//        }
//
//        // no join
//        if (joinParts.size() < 2) {
//            return "";
//        }
//        String left = "";
//        ArrayList<String> conds = new ArrayList<>(Arrays.asList(whereClause.getChild(1).getText().split("and")));
////
////        System.out.println("size " + conds.size());
////        System.out.println(whereClause.getChild(0).getText());
////        for (int i = 0; i < conds.size();i++) {
////            System.out.println(conds.get(i));
////        }
//
//        while (joinParts.size() >= 2) {
//            System.out.println("size: " +joinParts.size() );
//            // for a condition, constitute the join attrilists
//            ArrayList<String> l = new ArrayList<>();
//            ArrayList<String> r = new ArrayList<>();
//            ArrayList<String> conditions = new ArrayList<>(conds);
//            for (int i = 0; i < conditions.size(); i++) {
//                String curCond = conditions.get(i);
//                String[] vars = curCond.split("eq|=");
//                LinkedHashMap<String,String> first = varMaps.get(vars[0]);
//                LinkedHashMap<String,String> second = varMaps.get(vars[1]);
//                if(first == second || !vars[0].contains("$") || !vars[1].contains("$")) {
//                    // filter inside one part
//                    System.out.println("constant:" +vars[0]);
//                    System.out.println("constant:" +vars[1]);
//
//                    int idx = joinParts.indexOf(first);
//                    if (whereCond.get(idx).isEmpty()) {
//                        whereCond.remove(idx);
//                        whereCond.add(idx,"where " + vars[0] + " eq " + vars[1] + " ");
//                    } else {
//                        String existConds = whereCond.get(idx);
//                        whereCond.remove(idx);
//                        whereCond.add(idx, existConds + "and" + vars[0] + " eq " + vars[1] + " ");
//                    }
//                    System.out.println("where " + whereCond.get(idx));
//                    conditions.remove(curCond);
//                } else if (first != second && first != null && second !=null && l.isEmpty()) {
//                    //no join conditions assigned
//                    System.out.println("left and right");
//
//                    l.add(vars[0]);
//                    r.add(vars[1]);
//                    conditions.remove(curCond);
//                } else if (first == varMaps.get(l.get(0)) && second == varMaps.get(r.get(0))) {
//                    //already assigned
//                    System.out.println("right and left");
//
//                    l.add(vars[0]);
//                    r.add(vars[1]);
//                    conditions.remove(curCond);
//                } else if (second == varMaps.get(l.get(0)) && first == varMaps.get(r.get(0))) {
//                    r.add(vars[0]);
//                    l.add(vars[1]);
//                    conditions.remove(curCond);
//                }
//            }
//            conds = conditions;
//            if (l.isEmpty()) {
//                System.out.println("l empty");
//                return "";
//            }
//            LinkedHashMap<String, String> first = varMaps.get(l.get(0));
//            LinkedHashMap<String, String> second = varMaps.get(r.get(0));
//            if(left.isEmpty()) {
//                left += "for ";
//                for (String var: first.keySet()) {
//                    left += var + " in " + first.get(var) + ",\n\t";
//                }
//                left = left.substring(0, left.length()-3);
//                String where = whereCond.get(joinParts.indexOf(first));
//                if (!where.isEmpty()) {
//                    left += "\n\t" + where;
//                }
//                left += "\n\treturn <tuple>{\n\t";
//                for (String var: first.keySet()) {
//                    left += "<" + var.substring(1, var.length()) + ">{" + var + "}" + "</" + var.substring(1, var.length()) + ">,\n\t";
//                }
//                left = left.substring(0, left.length()-3) + "\n\t}</tuple>,";
//            }
////            System.out.println(left);
//            left = "join(\n\t" + left;
//            left += "\n\tfor ";
//            for (String var : second.keySet()){
//                left += var + " in " + second.get(var) + ",\n\t";
//            }
//            left = left.substring(0, left.length()-3);
//            String where = whereCond.get(joinParts.indexOf(second));
//            if (!where.isEmpty()){
//                System.out.println("where" + where);
//                left += "\n\t" + where;
//            }
//            left += "\n\treturn <tuple>{\n\t";
//            for (String var : second.keySet()){
//                left += "<" + var.substring(1, var.length()) + ">{" + var + "}" + "</" + var.substring(1, var.length()) + ">,\n\t";
//            }
//            left = left.substring(0, left.length()-3) + "\n\t}</tuple>,";
//            left += "\n\t[";
//            for (String var : l){
//                left += var.substring(1, var.length()) + ",";
//            }
//            left = left.substring(0, left.length()-1) + "],[";
//            for (String var : r){
//                left += var.substring(1, var.length()) + ",";
//            }
//            left = left.substring(0, left.length()-1) + "]),";
//
//            for(Map.Entry<String, String> e: second.entrySet()) {
//                first.put(e.getKey(), e.getValue());
//                varMaps.replace(e.getKey(), first);
//            }
//
//            whereCond.remove(joinParts.indexOf(second));
//            joinParts.remove(second);
//        }
//        res += left.substring(0, left.length()-1);
//        String returnText = returnClause.getChild(1).getText();
//        int i = 0;
//        while(true) {
//            if(i >= returnText.length())
//                break;
//            if(returnText.charAt(i) == '$') {
//                int idx = i+1;
//                while(true) {
//                    char tmp = returnText.charAt(idx);
//                    if(tmp == ',' || tmp == '/' || tmp == '}')
//                        break;
//                    else
//                        idx++;
//                }
//                returnText = returnText.substring(0,idx) + "/*" + returnText.substring(idx,returnText.length());
//            }
//            i++;
//        }
//        returnText = returnText.replace("{","{\n\t").replace("$", "($tuple/").replace(",","),\n\t").replace("}",")\n\t}");
//
//        res += "\nreturn " + returnText;
//
//        if(rstTag != null) {
//            res = "<" + rstTag + ">{\n" + res + "\n}</" + rstTag + ">";
//        }
//        System.out.print("rst:" + res);
//        return res;
//    }


}